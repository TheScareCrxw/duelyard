import type { ComponentType } from "react";
import type { RoomState } from "@/types/realtime";

/** One move's events from the server, numbered so components can tell new batches apart. */
export interface EventBatch {
  id: number;
  actorSeat: number;
  timedOut: boolean;
  events: unknown[];
  /** The view before these events were applied (for animating from the old state). */
  previousView: unknown;
  /** performance.now() when received: the time base for canvas animations. */
  receivedAt: number;
  /** Date.now() when received: compared with `now` to know whether an animation is still running. */
  receivedWall: number;
}

export interface GameProps<V> {
  room: RoomState<V>;
  batch: EventBatch | null;
  /** True when it's this player's turn and the match is in play. */
  canAct: boolean;
  act: (input: Record<string, number>) => void;
  /** Wall-clock time, refreshed a few times a second by the match view. */
  now: number;
}

/** True while a batch's animation of `durationMs` is still playing. */
export function isAnimating(batch: EventBatch | null, durationMs: number, now: number) {
  return batch !== null && now - batch.receivedWall < durationMs;
}

export interface GameRenderer<V> {
  Component: ComponentType<GameProps<V>>;
  /** Short score shown next to each player in the header. */
  score: (view: V, seat: number) => string;
}

/** Progress 0..1 of an animation that started at `start` and lasts `durationMs`. */
export function progress(now: number, start: number, durationMs: number) {
  return Math.min(1, Math.max(0, (now - start) / durationMs));
}
