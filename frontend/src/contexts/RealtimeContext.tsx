"use client";

import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { RealtimeClient } from "@/lib/realtime/RealtimeClient";
import type { ConnectionStatus, ServerMessage } from "@/types/realtime";

interface RealtimeValue {
  client: RealtimeClient;
  status: ConnectionStatus;
  /** The match the server says this user is in, from the latest welcome. */
  activeMatchId: string | null;
}

const RealtimeContext = createContext<RealtimeValue | null>(null);

/** One game-server connection shared by the play and match pages. */
export function RealtimeProvider({ children }: { children: ReactNode }) {
  const [client] = useState(() => new RealtimeClient());
  const [status, setStatus] = useState<ConnectionStatus>("connecting");
  const [activeMatchId, setActiveMatchId] = useState<string | null>(null);

  useEffect(() => {
    const offStatus = client.onStatus(setStatus);
    const offMessages = client.subscribe((m: ServerMessage) => {
      if (m.type === "welcome") setActiveMatchId(m.activeMatchId);
      if (m.type === "match.found") setActiveMatchId(m.matchId);
      if (m.type === "match.ended") setActiveMatchId(null);
    });
    client.start();
    return () => {
      offStatus();
      offMessages();
      client.stop();
    };
  }, [client]);

  return <RealtimeContext.Provider value={{ client, status, activeMatchId }}>{children}</RealtimeContext.Provider>;
}

export function useRealtime(): RealtimeValue {
  const value = useContext(RealtimeContext);
  if (!value) throw new Error("useRealtime must be used inside RealtimeProvider");
  return value;
}

/** Subscribes to server messages for the component's lifetime. */
export function useServerMessages(listener: (message: ServerMessage) => void) {
  const { client } = useRealtime();
  const latest = useRef(listener);
  useEffect(() => {
    latest.current = listener;
  });
  useEffect(() => client.subscribe((m) => latest.current(m)), [client]);
}
