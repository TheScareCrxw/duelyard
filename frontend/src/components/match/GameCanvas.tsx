"use client";

import { useEffect, useRef, type PointerEvent } from "react";

export interface CanvasPointer {
  x: number;
  y: number;
  type: "down" | "move" | "up" | "leave";
}

interface GameCanvasProps {
  /** Size of the game's coordinate space; the canvas scales it to the container width. */
  width: number;
  height: number;
  /** Called every animation frame with a context already scaled to game coordinates. */
  draw: (ctx: CanvasRenderingContext2D, now: number) => void;
  onPointer?: (p: CanvasPointer) => void;
  className?: string;
  ariaLabel: string;
}

/** Responsive, high-DPI canvas with a continuous draw loop and pointer events in game coordinates. */
export function GameCanvas({ width, height, draw, onPointer, className = "", ariaLabel }: GameCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawRef = useRef(draw);
  useEffect(() => {
    drawRef.current = draw;
  });

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    let frame = 0;
    const render = (now: number) => {
      const dpr = window.devicePixelRatio || 1;
      const cssWidth = canvas.clientWidth;
      const scale = cssWidth / width;
      const pxW = Math.round(cssWidth * dpr);
      const pxH = Math.round(height * scale * dpr);
      if (canvas.width !== pxW || canvas.height !== pxH) {
        canvas.width = pxW;
        canvas.height = pxH;
      }
      const ctx = canvas.getContext("2d");
      if (ctx) {
        ctx.setTransform(scale * dpr, 0, 0, scale * dpr, 0, 0);
        ctx.clearRect(0, 0, width, height);
        drawRef.current(ctx, now);
      }
      frame = requestAnimationFrame(render);
    };
    frame = requestAnimationFrame(render);
    return () => cancelAnimationFrame(frame);
  }, [width, height]);

  const pointer = (type: CanvasPointer["type"]) => (e: PointerEvent<HTMLCanvasElement>) => {
    if (!onPointer) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const x = ((e.clientX - rect.left) / rect.width) * width;
    const y = ((e.clientY - rect.top) / rect.height) * height;
    if (type === "down") e.currentTarget.setPointerCapture(e.pointerId);
    onPointer({ x, y, type });
  };

  return (
    <canvas
      ref={canvasRef}
      role="img"
      aria-label={ariaLabel}
      className={`block w-full touch-none select-none ${className}`}
      style={{ aspectRatio: `${width} / ${height}` }}
      onPointerDown={pointer("down")}
      onPointerMove={pointer("move")}
      onPointerUp={pointer("up")}
      onPointerLeave={pointer("leave")}
    />
  );
}
