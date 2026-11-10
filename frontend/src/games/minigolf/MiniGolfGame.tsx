"use client";

import { useRef } from "react";
import { GameCanvas, type CanvasPointer } from "@/components/match/GameCanvas";
import { isAnimating, type GameProps, type GameRenderer } from "../types";

/** Mirrors MiniGolfSession.View / Course / Stroke on the server. */
interface Course {
  name: string;
  par: number;
  width: number;
  height: number;
  boundary: number[][];
  walls: number[][];
  bumpers: number[][];
  tee: number[];
  cup: number[];
}
export interface MiniGolfView {
  hole: number;
  holeCount: number;
  course: Course;
  balls: number[][];
  done: boolean[];
  strokes: number[][];
  totals: number[];
  turnSeat: number;
}
interface Stroke {
  seat: number;
  hole: number;
  path: number[][];
  holed: boolean;
  strokes: number;
  timedOut: boolean;
}

const BALL_R = 6;
const CUP_R = 10;
const MAX_PULL = 170;
const FRAME_MS = 1000 / 30;
const SEAT_COLORS = ["#c8ff3d", "#4f8dff"];

function drawCourse(ctx: CanvasRenderingContext2D, course: Course) {
  ctx.fillStyle = "#07080c";
  ctx.fillRect(0, 0, course.width, course.height);
  ctx.beginPath();
  course.boundary.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.closePath();
  ctx.fillStyle = "#1c6b3c";
  ctx.fill();
  ctx.strokeStyle = "#8b5a2b";
  ctx.lineWidth = 6;
  ctx.lineJoin = "round";
  ctx.stroke();
  ctx.lineCap = "round";
  for (const [x1, y1, x2, y2] of course.walls) {
    ctx.beginPath();
    ctx.moveTo(x1, y1);
    ctx.lineTo(x2, y2);
    ctx.stroke();
  }
  for (const [x, y, r] of course.bumpers) {
    ctx.fillStyle = "#ff5a36";
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#ffd2c7";
    ctx.lineWidth = 2;
    ctx.stroke();
  }
  ctx.fillStyle = "#0a0b10";
  ctx.beginPath();
  ctx.arc(course.cup[0], course.cup[1], CUP_R, 0, Math.PI * 2);
  ctx.fill();
  // Flag
  ctx.strokeStyle = "#eef0f6";
  ctx.lineWidth = 1.5;
  ctx.beginPath();
  ctx.moveTo(course.cup[0], course.cup[1]);
  ctx.lineTo(course.cup[0], course.cup[1] - 34);
  ctx.stroke();
  ctx.fillStyle = "#ff4d6a";
  ctx.beginPath();
  ctx.moveTo(course.cup[0], course.cup[1] - 34);
  ctx.lineTo(course.cup[0] + 16, course.cup[1] - 28);
  ctx.lineTo(course.cup[0], course.cup[1] - 22);
  ctx.fill();
}

function drawBall(ctx: CanvasRenderingContext2D, x: number, y: number, seat: number, faded = false) {
  ctx.globalAlpha = faded ? 0.35 : 1;
  ctx.fillStyle = "#f4f6fb";
  ctx.beginPath();
  ctx.arc(x, y, BALL_R, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = SEAT_COLORS[seat];
  ctx.lineWidth = 2.5;
  ctx.stroke();
  ctx.globalAlpha = 1;
}

const isStroke = (e: unknown): e is Stroke => typeof e === "object" && e !== null && "path" in e;

export function MiniGolfGame({ room, batch, canAct, act, now }: GameProps<MiniGolfView>) {
  const drag = useRef<[number, number] | null>(null);
  const seat = room.yourSeat;

  // While a stroke animates, keep showing the hole it was played on.
  const stroke = batch?.events.find(isStroke);
  const duration = stroke && stroke.path.length > 1 ? stroke.path.length * FRAME_MS + 400 : 0;
  const animating = duration > 0 && isAnimating(batch, duration, now);
  const previous = batch?.previousView as MiniGolfView | undefined;
  const view = animating && previous ? previous : room.view;
  const course = view.course;

  const draw = (ctx: CanvasRenderingContext2D, t: number) => {
    const active =
      batch && stroke && previous && duration > 0 && t - batch.receivedAt < duration
        ? { stroke, view: previous, start: batch.receivedAt }
        : null;
    const v = active ? active.view : room.view;
    drawCourse(ctx, v.course);
    for (let s = 0; s < 2; s++) {
      if (active && s === active.stroke.seat) continue;
      if (!v.done[s]) drawBall(ctx, v.balls[s][0], v.balls[s][1], s, v.turnSeat !== s);
    }
    if (active) {
      const i = Math.min(active.stroke.path.length - 1, Math.floor((t - active.start) / FRAME_MS));
      const [x, y] = active.stroke.path[i];
      const sunk = active.stroke.holed && i === active.stroke.path.length - 1;
      if (!sunk) drawBall(ctx, x, y, active.stroke.seat);
    }

    const me = room.view.balls[seat];
    if (canAct && !active && drag.current && me) {
      const [px, py] = drag.current;
      const dx = me[0] - px, dy = me[1] - py;
      const pull = Math.min(MAX_PULL, Math.hypot(dx, dy));
      const angle = Math.atan2(dy, dx);
      ctx.strokeStyle = "rgba(200,255,61,0.9)";
      ctx.setLineDash([6, 6]);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(me[0], me[1]);
      ctx.lineTo(me[0] + Math.cos(angle) * pull * 1.3, me[1] + Math.sin(angle) * pull * 1.3);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = "rgba(7,8,12,0.75)";
      ctx.fillRect(8, 8, 104, 12);
      ctx.fillStyle = pull / MAX_PULL > 0.85 ? "#ff4d6a" : "#c8ff3d";
      ctx.fillRect(10, 10, (pull / MAX_PULL) * 100, 8);
    }
  };

  const onPointer = (p: CanvasPointer) => {
    if (!canAct || animating) {
      drag.current = null;
      return;
    }
    if (p.type === "down") drag.current = [p.x, p.y];
    if (p.type === "move" && drag.current) drag.current = [p.x, p.y];
    if (p.type === "up" && drag.current) {
      const me = room.view.balls[seat];
      const dx = me[0] - p.x, dy = me[1] - p.y;
      const pull = Math.min(MAX_PULL, Math.hypot(dx, dy));
      drag.current = null;
      if (pull > 8) act({ angle: Math.atan2(dy, dx), power: Math.max(0.02, pull / MAX_PULL) });
    }
  };

  return (
    <div className="mx-auto max-w-[400px]">
      <div className="mb-2 flex items-baseline justify-between">
        <h3 className="font-semibold">
          Hole {view.hole + 1}/{view.holeCount}: {course.name}
        </h3>
        <span className="text-xs text-muted">Par {course.par}</span>
      </div>
      <GameCanvas
        width={course.width}
        height={course.height}
        draw={draw}
        onPointer={onPointer}
        className={canAct ? "cursor-grab" : ""}
        ariaLabel={`Mini golf hole ${view.hole + 1}: ${course.name}`}
      />
      <p className="mt-2 text-xs text-faint">
        Drag back from anywhere and release to putt: direction is away from your pull, distance sets power. Max 6 strokes per hole.
      </p>
      <table className="mt-3 w-full text-center text-xs tabular-nums">
        <thead className="text-faint">
          <tr>
            <th className="text-left font-medium">Player</th>
            {Array.from({ length: room.view.holeCount }, (_, h) => (
              <th key={h} className="font-medium">
                {h + 1}
              </th>
            ))}
            <th className="font-medium">Total</th>
          </tr>
        </thead>
        <tbody>
          {room.players.map((p) => (
            <tr key={p.seat}>
              <td className="max-w-24 truncate py-1 text-left" style={{ color: SEAT_COLORS[p.seat] }}>
                {p.username}
              </td>
              {room.view.strokes[p.seat].map((n, h) => (
                <td key={h}>{n || "–"}</td>
              ))}
              <td className="font-semibold">{room.view.totals[p.seat]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export const miniGolfRenderer: GameRenderer<MiniGolfView> = {
  Component: MiniGolfGame,
  score: (view, seat) => String(view.totals[seat]),
};
