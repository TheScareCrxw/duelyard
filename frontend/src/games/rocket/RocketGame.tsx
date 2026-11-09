"use client";

import { useState } from "react";
import { GameCanvas, type CanvasPointer } from "@/components/match/GameCanvas";
import { buttonClasses } from "@/components/ui/Button";
import { isAnimating, type GameProps, type GameRenderer } from "../types";

/** Mirrors RocketSession.View / ShotFired on the server. World 1000 x 500, y down. */
export interface RocketView {
  terrain: number[];
  launchers: number[][];
  hp: number[];
  wind: number;
  shotsFired: number;
  maxShots: number;
  turnSeat: number;
}
interface ShotFired {
  seat: number;
  path: number[][];
  impact: number[] | null;
  damage: number[];
  hp: number[];
}

const W = 1000;
const H = 500;
const STEP = 5;
const FRAME_MS = 1000 / 30;
const BLAST_MS = 600;
const SEAT_COLORS = ["#c8ff3d", "#4f8dff"];

function drawWorld(ctx: CanvasRenderingContext2D, v: RocketView, aim: { seat: number; angle: number } | null) {
  const sky = ctx.createLinearGradient(0, 0, 0, H);
  sky.addColorStop(0, "#0b1022");
  sky.addColorStop(1, "#1d1830");
  ctx.fillStyle = sky;
  ctx.fillRect(0, 0, W, H);

  ctx.beginPath();
  ctx.moveTo(0, H);
  v.terrain.forEach((y, i) => ctx.lineTo(i * STEP, y));
  ctx.lineTo(W, H);
  ctx.closePath();
  ctx.fillStyle = "#3b2f4f";
  ctx.fill();
  ctx.strokeStyle = "#8f7dff";
  ctx.lineWidth = 2;
  ctx.beginPath();
  v.terrain.forEach((y, i) => (i ? ctx.lineTo(i * STEP, y) : ctx.moveTo(0, y)));
  ctx.stroke();

  v.launchers.forEach(([x, y], seat) => {
    ctx.fillStyle = SEAT_COLORS[seat];
    ctx.beginPath();
    ctx.roundRect(x - 16, y - 12, 32, 12, 4);
    ctx.fill();
    const angle = aim && aim.seat === seat ? aim.angle : seat === 0 ? 45 : 135;
    const rad = (angle * Math.PI) / 180;
    ctx.strokeStyle = SEAT_COLORS[seat];
    ctx.lineWidth = 5;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(x, y - 12);
    ctx.lineTo(x + Math.cos(rad) * 24, y - 12 - Math.sin(rad) * 24);
    ctx.stroke();
    // HP bar
    ctx.fillStyle = "#151824";
    ctx.fillRect(x - 25, y - 34, 50, 6);
    ctx.fillStyle = v.hp[seat] > 30 ? "#34d27b" : "#ff4d6a";
    ctx.fillRect(x - 25, y - 34, (50 * v.hp[seat]) / 100, 6);
  });
}

const isShot = (e: unknown): e is ShotFired => typeof e === "object" && e !== null && "path" in e;

export function RocketGame({ room, batch, canAct, act, now }: GameProps<RocketView>) {
  const seat = room.yourSeat;
  const [angle, setAngle] = useState(seat === 0 ? 45 : 135);
  const [power, setPower] = useState(0.6);
  const view = room.view;
  const shot = batch?.events.find(isShot);
  const duration = shot ? shot.path.length * FRAME_MS + BLAST_MS : 0;
  const animating = shot !== undefined && isAnimating(batch, duration, now);

  const draw = (ctx: CanvasRenderingContext2D, t: number) => {
    const anim =
      batch && shot && t - batch.receivedAt < duration
        ? { shot, view: batch.previousView as RocketView, start: batch.receivedAt }
        : null;
    if (!anim) {
      drawWorld(ctx, view, canAct ? { seat, angle } : null);
      return;
    }
    const flight = anim.shot.path.length * FRAME_MS;
    const elapsed = t - anim.start;
    // Before the impact, show the old terrain and HP; after it, the new.
    drawWorld(ctx, elapsed < flight ? anim.view : view, null);
    const i = Math.min(anim.shot.path.length - 1, Math.floor(elapsed / FRAME_MS));
    ctx.strokeStyle = "rgba(255,174,43,0.45)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    anim.shot.path.slice(0, i + 1).forEach(([x, y], k) => (k ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
    ctx.stroke();
    if (elapsed < flight) {
      const [x, y] = anim.shot.path[i];
      ctx.fillStyle = "#ffae2b";
      ctx.beginPath();
      ctx.arc(x, y, 5, 0, Math.PI * 2);
      ctx.fill();
    } else if (anim.shot.impact) {
      const k = Math.min(1, (elapsed - flight) / BLAST_MS);
      ctx.fillStyle = `rgba(255,120,40,${0.7 * (1 - k)})`;
      ctx.beginPath();
      ctx.arc(anim.shot.impact[0], anim.shot.impact[1], 20 + k * 40, 0, Math.PI * 2);
      ctx.fill();
      anim.shot.damage.forEach((d, s) => {
        if (!d) return;
        const [lx, ly] = view.launchers[s];
        ctx.fillStyle = "#ff4d6a";
        ctx.font = "700 20px ui-sans-serif, system-ui";
        ctx.textAlign = "center";
        ctx.fillText(`−${d}`, lx, ly - 44 - k * 20);
      });
    }
  };

  const onPointer = (p: CanvasPointer) => {
    if (!canAct || p.type === "leave" || p.type === "up") return;
    const [lx, ly] = view.launchers[seat];
    const a = (Math.atan2(ly - 12 - p.y, p.x - lx) * 180) / Math.PI;
    setAngle(Math.round(Math.min(180, Math.max(0, a))));
  };

  const windText = view.wind === 0 ? "No wind" : `Wind ${view.wind > 0 ? "→" : "←"} ${Math.abs(view.wind)}`;

  return (
    <div>
      <div className="mb-2 flex justify-between text-xs text-muted">
        <span>{windText}</span>
        <span>
          Shot {Math.min(view.shotsFired + 1, view.maxShots)}/{view.maxShots}
        </span>
      </div>
      <GameCanvas width={W} height={H} draw={draw} onPointer={onPointer} className={canAct ? "cursor-crosshair" : ""} ariaLabel="Artillery field" />
      <div className="mt-3 grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-center">
        <label className="flex items-center gap-3 text-sm">
          <span className="w-12 text-muted">Angle</span>
          <input type="range" min={0} max={180} value={angle} onChange={(e) => setAngle(Number(e.target.value))} disabled={!canAct} className="w-full accent-[#c8ff3d]" aria-label="Angle" />
          <span className="w-10 text-right font-mono tabular-nums">{angle}°</span>
        </label>
        <label className="flex items-center gap-3 text-sm">
          <span className="w-12 text-muted">Power</span>
          <input type="range" min={0} max={100} value={Math.round(power * 100)} onChange={(e) => setPower(Number(e.target.value) / 100)} disabled={!canAct} className="w-full accent-[#c8ff3d]" aria-label="Power" />
          <span className="w-10 text-right font-mono tabular-nums">{Math.round(power * 100)}%</span>
        </label>
        <button type="button" disabled={!canAct || animating} onClick={() => act({ angle, power })} className={buttonClasses("primary", "md")}>
          Fire
        </button>
      </div>
      <p className="mt-2 text-xs text-faint">Point at the sky or use the sliders to aim. Wind changes every turn. Blasts damage anyone nearby, including you.</p>
    </div>
  );
}

export const rocketRenderer: GameRenderer<RocketView> = {
  Component: RocketGame,
  score: (view, seat) => `${view.hp[seat]} HP`,
};
