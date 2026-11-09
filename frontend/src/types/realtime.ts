import type { GameMode } from "./game";

/** Mirrors com.duelyard.multiplayer.Messages. See docs/games.md. */

export interface PlayerSummary {
  seat: number;
  username: string;
  avatarUrl: string | null;
  rating: number;
  connected: boolean;
}

export type RoomPhase = "COUNTDOWN" | "PLAYING" | "ENDED";

export interface RoomState<V = unknown> {
  matchId: string;
  game: string;
  mode: GameMode;
  phase: RoomPhase;
  yourSeat: number;
  players: PlayerSummary[];
  /** Epoch ms when play starts. */
  startsAt: number;
  /** -1 when nobody can move. */
  turnSeat: number;
  /** Epoch ms when the current turn times out. */
  turnDeadline: number | null;
  view: V;
}

export interface SeatResult {
  seat: number;
  username: string;
  result: "WIN" | "LOSS" | "DRAW" | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
}

export type ServerMessage =
  | { type: "welcome"; username: string; activeMatchId: string | null }
  | { type: "queue.status"; game: string; mode: GameMode; waitedSeconds: number; ratingRange: number | null }
  | { type: "queue.left"; reason: string }
  | { type: "match.found"; matchId: string; game: string; mode: GameMode; opponent: PlayerSummary; startsAt: number }
  | { type: "match.state"; state: RoomState }
  | { type: "match.events"; matchId: string; actorSeat: number; timedOut: boolean; events: unknown[]; state: RoomState }
  | { type: "match.ended"; matchId: string; reason: string; results: SeatResult[] }
  | { type: "opponent.connection"; matchId: string; connected: boolean; graceSeconds: number }
  | { type: "error"; code: string; message: string }
  | { type: "pong" };

export type ClientMessage =
  | { type: "queue.join"; game: string; mode: GameMode }
  | { type: "queue.leave" }
  | { type: "match.action"; matchId: string; input: Record<string, number> }
  | { type: "match.sync"; matchId: string }
  | { type: "ping" };

export type ConnectionStatus = "connecting" | "open" | "reconnecting" | "closed" | "unauthenticated";
