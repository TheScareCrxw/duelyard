import type { ClientMessage, ConnectionStatus, ServerMessage } from "@/types/realtime";

type Listener = (message: ServerMessage) => void;
type StatusListener = (status: ConnectionStatus) => void;

const REALTIME_URL = process.env.NEXT_PUBLIC_REALTIME_URL ?? "ws://localhost:8080/ws";
const MAX_BACKOFF_MS = 10_000;
const PING_INTERVAL_MS = 25_000;

/**
 * Browser WebSocket connection to the game server. Gets a one-time ticket from this app's own
 * server (which holds the session cookie), connects, and reconnects with exponential backoff.
 *
 * Only one connection attempt is ever current: each attempt carries a generation number, and an
 * attempt (or socket) from an older generation discards itself. Messages sent while not connected
 * are dropped; pages re-send their intent when the server's `welcome` arrives on a new connection.
 */
export class RealtimeClient {
  private socket: WebSocket | null = null;
  private generation = 0;
  private listeners = new Set<Listener>();
  private statusListeners = new Set<StatusListener>();
  private status: ConnectionStatus = "closed";
  private attempts = 0;
  private stopped = true;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private pingTimer: ReturnType<typeof setInterval> | null = null;

  start() {
    if (!this.stopped) return;
    this.stopped = false;
    void this.open();
  }

  stop() {
    this.stopped = true;
    this.generation++;
    this.clearTimers();
    this.socket?.close(1000);
    this.socket = null;
    this.setStatus("closed");
  }

  send(message: ClientMessage): boolean {
    if (this.socket?.readyState !== WebSocket.OPEN) return false;
    this.socket.send(JSON.stringify(message));
    return true;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  onStatus(listener: StatusListener): () => void {
    this.statusListeners.add(listener);
    listener(this.status);
    return () => this.statusListeners.delete(listener);
  }

  private setStatus(status: ConnectionStatus) {
    this.status = status;
    this.statusListeners.forEach((l) => l(status));
  }

  private clearTimers() {
    if (this.retryTimer) clearTimeout(this.retryTimer);
    if (this.pingTimer) clearInterval(this.pingTimer);
    this.retryTimer = null;
    this.pingTimer = null;
  }

  private async open() {
    if (this.stopped) return;
    const generation = ++this.generation;
    const current = () => !this.stopped && generation === this.generation;
    this.setStatus(this.attempts === 0 ? "connecting" : "reconnecting");

    let ticket: string;
    try {
      const response = await fetch("/api/realtime/ticket", { method: "POST", cache: "no-store" });
      if (!current()) return;
      if (response.status === 401) {
        this.stopped = true;
        this.setStatus("unauthenticated");
        return;
      }
      if (!response.ok) throw new Error(`ticket ${response.status}`);
      ticket = ((await response.json()) as { ticket: string }).ticket;
    } catch {
      if (current()) this.scheduleRetry();
      return;
    }
    if (!current()) return;

    const socket = new WebSocket(`${REALTIME_URL}?ticket=${encodeURIComponent(ticket)}`);
    this.socket = socket;
    socket.onopen = () => {
      if (this.socket !== socket) return socket.close(1000);
      this.attempts = 0;
      this.setStatus("open");
      if (this.pingTimer) clearInterval(this.pingTimer);
      this.pingTimer = setInterval(() => this.send({ type: "ping" }), PING_INTERVAL_MS);
    };
    socket.onmessage = (event) => {
      if (this.socket !== socket) return;
      let message: ServerMessage;
      try {
        message = JSON.parse(event.data as string) as ServerMessage;
      } catch {
        return;
      }
      this.listeners.forEach((l) => l(message));
    };
    socket.onclose = (event) => {
      if (this.socket !== socket) return;
      this.socket = null;
      this.clearTimers();
      // 4000: this account connected from another tab. Don't fight it.
      if (event.code === 4000) {
        this.stopped = true;
        this.setStatus("closed");
        return;
      }
      this.scheduleRetry();
    };
  }

  private scheduleRetry() {
    if (this.stopped) return;
    this.setStatus("reconnecting");
    const delay = Math.min(MAX_BACKOFF_MS, 500 * 2 ** this.attempts) * (0.75 + Math.random() * 0.5);
    this.attempts++;
    this.retryTimer = setTimeout(() => void this.open(), delay);
  }
}
