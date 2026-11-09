"use client";

import { useRef } from "react";
import { GameCanvas, type CanvasPointer } from "@/components/match/GameCanvas";
import type { GameProps, GameRenderer } from "../types";

/** Mirrors DartsSession.View / Throw on the server. */
interface Throw {
  seat: number;
  x: number;
  y: number;
  points: number;
  label: string;
  bust: boolean;
  remaining: number;
  finished: boolean;
}
export interface DartsView {
  scores: number[];
  round: number;
  maxRounds: number;
  turnSeat: number;
  dartsLeft: number;
  currentTurn: Throw[];
  lastTurn: Throw[];
}

const SIZE = 460;
const C = SIZE / 2;
const S = 1.15; // px per mm
const SEGMENTS = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5];
const SEAT_COLORS = ["#c8ff3d", "#4f8dff"];

const toPx = (x: number, y: number) => [C + x * S, C - y * S] as const;

function drawBoard(ctx: CanvasRenderingContext2D) {
  ctx.fillStyle = "#0b0d14";
  ctx.beginPath();
  ctx.arc(C, C, 200, 0, Math.PI * 2);
  ctx.fill();
  const ring = (r0: number, r1: number, a0: number, a1: number, color: string) => {
    ctx.beginPath();
    ctx.arc(C, C, r1 * S, a0, a1);
    ctx.arc(C, C, r0 * S, a1, a0, true);
    ctx.closePath();
    ctx.fillStyle = color;
    ctx.fill();
  };
  for (let i = 0; i < 20; i++) {
    const a0 = ((-99 + 18 * i) * Math.PI) / 180;
    const a1 = ((-81 + 18 * i) * Math.PI) / 180;
    const dark = i % 2 === 0;
    ring(15.9, 99, a0, a1, dark ? "#1a1d27" : "#e9e3cf");
    ring(107, 162, a0, a1, dark ? "#1a1d27" : "#e9e3cf");
    ring(99, 107, a0, a1, dark ? "#d8394f" : "#1f9e5a");
    ring(162, 170, a0, a1, dark ? "#d8394f" : "#1f9e5a");
    const mid = ((-90 + 18 * i) * Math.PI) / 180;
    ctx.fillStyle = "#b4bccd";
    ctx.font = "600 15px ui-sans-serif, system-ui";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(String(SEGMENTS[i]), C + Math.cos(mid) * 184 * S, C + Math.sin(mid) * 184 * S);
  }
  ctx.fillStyle = "#1f9e5a";
  ctx.beginPath();
  ctx.arc(C, C, 15.9 * S, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = "#d8394f";
  ctx.beginPath();
  ctx.arc(C, C, 6.35 * S, 0, Math.PI * 2);
  ctx.fill();
}

function drawDart(ctx: CanvasRenderingContext2D, t: Throw, alpha = 1) {
  if (t.label === "MISS" && t.x === 0 && t.y === 0) return; // timed-out dart: never thrown
  const [px, py] = toPx(t.x, t.y);
  ctx.globalAlpha = alpha;
  ctx.strokeStyle = "#0b0d14";
  ctx.lineWidth = 2;
  ctx.fillStyle = SEAT_COLORS[t.seat];
  ctx.beginPath();
  ctx.arc(px, py, 5, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();
  ctx.globalAlpha = 1;
}

/** Release meter value 0..1 oscillating over time; the sweet spot is 0.45-0.55. */
const meterAt = (now: number) => 0.5 + 0.5 * Math.sin(now / 450);

const isThrow = (e: unknown): e is Throw => typeof e === "object" && e !== null && "label" in e;
const LABEL_MS = 1400;

export function DartsGame({ room, batch, canAct, act }: GameProps<DartsView>) {
  const view = room.view;
  const aim = useRef<[number, number] | null>(null);
  const meterRef = useRef(0.5);
  const lastThrow = batch?.events.filter(isThrow).at(-1);

  const draw = (ctx: CanvasRenderingContext2D, now: number) => {
    drawBoard(ctx);
    const shown = view.currentTurn.length ? view.currentTurn : view.lastTurn;
    shown.forEach((t) => drawDart(ctx, t, view.currentTurn.length ? 1 : 0.45));

    if (batch && lastThrow && now - batch.receivedAt < LABEL_MS) {
      const t = lastThrow;
      const [px, py] = toPx(t.x, t.y);
      const k = (now - batch.receivedAt) / LABEL_MS;
      ctx.fillStyle = "#eef0f6";
      ctx.font = "700 22px ui-sans-serif, system-ui";
      ctx.textAlign = "center";
      ctx.globalAlpha = 1 - k;
      ctx.fillText(t.bust ? "BUST" : t.points ? `${t.label} · ${t.points}` : "MISS", px, py - 18 - k * 20);
      ctx.globalAlpha = 1;
      ctx.strokeStyle = SEAT_COLORS[t.seat];
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.arc(px, py, 6 + k * 18, 0, Math.PI * 2);
      ctx.stroke();
    }

    if (canAct && aim.current) {
      const [ax, ay] = aim.current;
      ctx.strokeStyle = "#c8ff3d";
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(ax, ay, 10, 0, Math.PI * 2);
      ctx.moveTo(ax - 16, ay);
      ctx.lineTo(ax + 16, ay);
      ctx.moveTo(ax, ay - 16);
      ctx.lineTo(ax, ay + 16);
      ctx.stroke();
    }

    if (canAct) {
      // Release meter on the right edge: the green band is the sweet spot.
      const m = meterAt(now);
      meterRef.current = m;
      const x = SIZE - 18, top = 40, h = SIZE - 80;
      ctx.fillStyle = "#151824";
      ctx.fillRect(x, top, 10, h);
      ctx.fillStyle = "rgba(52,210,123,0.6)";
      ctx.fillRect(x, top + h * 0.45, 10, h * 0.1);
      ctx.fillStyle = "#eef0f6";
      ctx.fillRect(x - 4, top + h * (1 - m) - 2, 18, 4);
    }
  };

  const onPointer = (p: CanvasPointer) => {
    if (p.type === "leave") aim.current = null;
    if (p.type === "move" || p.type === "down") aim.current = [p.x, p.y];
    if (p.type === "down" && canAct && Math.hypot(p.x - C, p.y - C) < 200) {
      act({ x: Math.round(((p.x - C) / S) * 10) / 10, y: Math.round((-(p.y - C) / S) * 10) / 10, release: meterRef.current });
    }
  };

  return (
    <div className="mx-auto max-w-[460px]">
      <GameCanvas
        width={SIZE}
        height={SIZE}
        draw={draw}
        onPointer={onPointer}
        className={canAct ? "cursor-crosshair" : ""}
        ariaLabel={`Dartboard. Round ${view.round} of ${view.maxRounds}.`}
      />
      <div className="mt-3 flex items-center justify-between text-sm text-muted">
        <span>
          Round {view.round}/{view.maxRounds}
        </span>
        <span>{canAct ? `Click the board to throw · ${view.dartsLeft} dart${view.dartsLeft === 1 ? "" : "s"} left` : " "}</span>
      </div>
      <p className="mt-2 text-xs text-faint">
        Aim with the crosshair and throw when the meter is in the green band: the further off, the wider your darts scatter.
        Reach exactly 0 to win; going under is a bust.
      </p>
    </div>
  );
}

export const dartsRenderer: GameRenderer<DartsView> = {
  Component: DartsGame,
  score: (view, seat) => String(view.scores[seat]),
};
