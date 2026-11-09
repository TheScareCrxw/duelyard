"use client";

import { useRef, useState } from "react";
import { GameCanvas, type CanvasPointer } from "@/components/match/GameCanvas";
import { buttonClasses } from "@/components/ui/Button";
import { isAnimating, type GameProps, type GameRenderer } from "../types";

/** Mirrors PoolSession.View / Shot on the server. Playfield 1000 x 500 inside the cushions. */
type Group = "SOLIDS" | "STRIPES";
export interface PoolView {
  balls: (number[] | null)[];
  groups: (Group | null)[];
  openTable: boolean;
  breakShot: boolean;
  turnSeat: number;
  remaining: number[];
}
interface Shot {
  seat: number;
  frames: (number[] | null)[][];
  potted: number[];
  foul: boolean;
  foulReason: string | null;
  continues: boolean;
  assignedGroup: Group | null;
}

const RAIL = 40;
const W = 1000 + RAIL * 2;
const H = 500 + RAIL * 2;
const R = 14;
const FRAME_MS = 1000 / 30;
const POCKETS: [number, number, number][] = [
  [0, 0, 25], [500, -4, 22], [1000, 0, 25], [0, 500, 25], [500, 504, 22], [1000, 500, 25],
];
const COLORS = ["#ffffff", "#f5c518", "#2a6bd6", "#d8394f", "#6d3fb8", "#f07c22", "#1f9e5a", "#8b1e2d", "#111111"];
const FOULS: Record<string, string> = {
  SCRATCH: "Scratch: cue ball potted",
  NO_CONTACT: "Foul: no ball hit",
  WRONG_BALL_FIRST: "Foul: wrong ball hit first",
  TIMEOUT: "Foul: shot clock expired",
};

function colorOf(n: number) {
  return n === 0 ? COLORS[0] : n === 8 ? COLORS[8] : COLORS[((n - 1) % 8) + 1];
}

function drawTable(ctx: CanvasRenderingContext2D) {
  ctx.fillStyle = "#4a2c17";
  ctx.beginPath();
  ctx.roundRect(0, 0, W, H, 28);
  ctx.fill();
  ctx.fillStyle = "#14633b";
  ctx.fillRect(RAIL, RAIL, 1000, 500);
  ctx.strokeStyle = "rgba(255,255,255,0.08)";
  ctx.beginPath();
  ctx.moveTo(RAIL + 250, RAIL);
  ctx.lineTo(RAIL + 250, RAIL + 500);
  ctx.stroke();
  for (const [x, y, r] of POCKETS) {
    ctx.fillStyle = "#050507";
    ctx.beginPath();
    ctx.arc(RAIL + x, RAIL + y, r, 0, Math.PI * 2);
    ctx.fill();
  }
}

function drawBall(ctx: CanvasRenderingContext2D, n: number, x: number, y: number) {
  const cx = RAIL + x, cy = RAIL + y;
  const stripe = n >= 9;
  ctx.fillStyle = stripe ? "#f4f1e8" : colorOf(n);
  ctx.beginPath();
  ctx.arc(cx, cy, R, 0, Math.PI * 2);
  ctx.fill();
  if (stripe) {
    ctx.save();
    ctx.beginPath();
    ctx.arc(cx, cy, R, 0, Math.PI * 2);
    ctx.clip();
    ctx.fillStyle = colorOf(n);
    ctx.fillRect(cx - R, cy - R * 0.55, R * 2, R * 1.1);
    ctx.restore();
  }
  if (n > 0) {
    ctx.fillStyle = "#f4f1e8";
    ctx.beginPath();
    ctx.arc(cx, cy, R * 0.5, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#111";
    ctx.font = "700 10px ui-sans-serif, system-ui";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(String(n), cx, cy + 0.5);
  }
  ctx.strokeStyle = "rgba(0,0,0,0.35)";
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.arc(cx, cy, R, 0, Math.PI * 2);
  ctx.stroke();
}

const isShot = (e: unknown): e is Shot => typeof e === "object" && e !== null && "frames" in e;

export function PoolGame({ room, batch, canAct, act, now }: GameProps<PoolView>) {
  const [power, setPower] = useState(0.55);
  const aim = useRef(0);
  const view = room.view;
  const cue = view.balls[0];
  const lastShot = batch?.events.find(isShot) ?? null;
  const duration = lastShot ? lastShot.frames.length * FRAME_MS + 200 : 0;
  const animating = lastShot !== null && isAnimating(batch, duration, now);

  const draw = (ctx: CanvasRenderingContext2D, t: number) => {
    drawTable(ctx);
    const playing = batch !== null && lastShot !== null && t - batch.receivedAt < duration;
    const frame =
      playing && batch && lastShot
        ? lastShot.frames[Math.min(lastShot.frames.length - 1, Math.floor((t - batch.receivedAt) / FRAME_MS))]
        : view.balls;
    frame.forEach((p, n) => p && drawBall(ctx, n, p[0], p[1]));

    if (canAct && !playing && cue) {
      const a = aim.current;
      const sx = RAIL + cue[0], sy = RAIL + cue[1];
      ctx.strokeStyle = "rgba(255,255,255,0.55)";
      ctx.setLineDash([8, 8]);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.lineTo(sx + Math.cos(a) * 1100, sy + Math.sin(a) * 1100);
      ctx.stroke();
      ctx.setLineDash([]);
      // Cue stick behind the ball, pulled back with power.
      const back = 24 + power * 70;
      ctx.strokeStyle = "#d9b38c";
      ctx.lineWidth = 7;
      ctx.lineCap = "round";
      ctx.beginPath();
      ctx.moveTo(sx - Math.cos(a) * back, sy - Math.sin(a) * back);
      ctx.lineTo(sx - Math.cos(a) * (back + 340), sy - Math.sin(a) * (back + 340));
      ctx.stroke();
    }
  };

  const onPointer = (p: CanvasPointer) => {
    if (!cue || p.type === "leave") return;
    aim.current = Math.atan2(p.y - RAIL - cue[1], p.x - RAIL - cue[0]);
  };

  const yourGroup = view.groups[room.yourSeat];

  return (
    <div>
      <GameCanvas width={W} height={H} draw={draw} onPointer={onPointer} className={canAct ? "cursor-crosshair" : ""} ariaLabel="Pool table" />
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <label className="flex flex-1 items-center gap-3 text-sm">
          <span className="text-muted">Power</span>
          <input
            type="range"
            min={5}
            max={100}
            value={Math.round(power * 100)}
            onChange={(e) => setPower(Number(e.target.value) / 100)}
            disabled={!canAct}
            className="w-full accent-[#c8ff3d]"
            aria-label="Shot power"
          />
          <span className="w-10 text-right font-mono tabular-nums">{Math.round(power * 100)}%</span>
        </label>
        <button
          type="button"
          disabled={!canAct || animating}
          onClick={() => act({ angle: aim.current, power })}
          className={buttonClasses("primary", "md")}
        >
          Shoot
        </button>
      </div>
      <div className="mt-2 flex flex-wrap justify-between gap-2 text-xs text-muted">
        <span>
          {view.openTable
            ? view.breakShot ? "Break shot" : "Open table: pot any ball to claim its group"
            : `You: ${yourGroup === "SOLIDS" ? "solids (1-7)" : "stripes (9-15)"} · ${view.remaining[room.yourSeat]} left${view.remaining[room.yourSeat] === 0 ? ": go for the 8" : ""}`}
        </span>
        {lastShot?.foul && lastShot.foulReason && <span className="text-loss">{FOULS[lastShot.foulReason] ?? "Foul"}</span>}
      </div>
      <p className="mt-1 text-xs text-faint">Point at the table to aim, set power, then shoot. Clear your group, then sink the 8.</p>
    </div>
  );
}

export const poolRenderer: GameRenderer<PoolView> = {
  Component: PoolGame,
  score: (view, seat) => (view.groups[seat] ? `${7 - view.remaining[seat]}/7` : "–"),
};
