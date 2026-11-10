"use client";

import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent } from "react";
import { linearScale, niceTicks } from "@/lib/chartScale";
import type { RatingHistory } from "@/types/player";

const HEIGHT = 220;
const MARGIN = { top: 16, right: 56, bottom: 28, left: 44 };
const DEFAULT_WIDTH = 640;

interface Datum {
  index: number;
  rating: number;
  change: number | null;
  at: string | null;
}

function toData(history: RatingHistory): Datum[] {
  return [
    { index: 0, rating: history.initialRating, change: null, at: null },
    ...history.points.map((p, i) => ({ index: i + 1, rating: p.rating, change: p.change, at: p.at })),
  ];
}

const formatChange = (c: number) => (c > 0 ? `+${c}` : c < 0 ? `−${Math.abs(c)}` : "±0");
const formatDate = (iso: string) => new Date(iso).toLocaleDateString("en-US", { month: "short", day: "numeric" });

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(DEFAULT_WIDTH);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(280, entry.contentRect.width)));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, width] as const;
}

/**
 * Rating over ranked matches for one game: a single 2px line, recessive gridlines, the current
 * value labelled at the end, and a crosshair + tooltip that snaps to the nearest match (pointer
 * and arrow keys). Every value is also available in the table view below the chart.
 */
export function RatingHistoryChart({ history, accent, gameName }: { history: RatingHistory; accent: string; gameName: string }) {
  const [containerRef, width] = useWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const data = toData(history);
  const last = data[data.length - 1];

  const ratings = data.map((d) => d.rating);
  const ticks = niceTicks(Math.min(...ratings), Math.max(...ratings));
  const x = linearScale(0, Math.max(1, data.length - 1), MARGIN.left, width - MARGIN.right);
  const y = linearScale(ticks[0], ticks[ticks.length - 1], HEIGHT - MARGIN.bottom, MARGIN.top);
  const path = data.map((d, i) => `${i === 0 ? "M" : "L"}${x(d.index).toFixed(1)},${y(d.rating).toFixed(1)}`).join("");

  const nearest = (clientX: number, rect: DOMRect) => {
    const px = clientX - rect.left;
    const step = (width - MARGIN.left - MARGIN.right) / Math.max(1, data.length - 1);
    return Math.min(data.length - 1, Math.max(0, Math.round((px - MARGIN.left) / step)));
  };
  const onPointerMove = (e: PointerEvent<SVGRectElement>) =>
    setActive(nearest(e.clientX, e.currentTarget.ownerSVGElement!.getBoundingClientRect()));
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key !== "ArrowLeft" && e.key !== "ArrowRight" && e.key !== "Home" && e.key !== "End") return;
    e.preventDefault();
    setActive((current) => {
      const i = current ?? data.length - 1;
      if (e.key === "Home") return 0;
      if (e.key === "End") return data.length - 1;
      return Math.min(data.length - 1, Math.max(0, i + (e.key === "ArrowRight" ? 1 : -1)));
    });
  };

  const point = active === null ? null : data[active];
  const describe = (d: Datum) =>
    d.index === 0 ? "Starting rating" : `Match ${d.index} · ${formatChange(d.change!)}${d.at ? ` · ${formatDate(d.at)}` : ""}`;

  return (
    <div>
      <div
        ref={containerRef}
        className="relative rounded-lg focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-volt"
        tabIndex={0}
        role="group"
        aria-label={`${gameName} rating over ${data.length - 1} ranked matches, now ${last.rating}. Use arrow keys to inspect.`}
        onKeyDown={onKeyDown}
        onFocus={() => setActive((a) => a ?? data.length - 1)}
        onBlur={() => setActive(null)}
      >
        <svg width={width} height={HEIGHT} className="block max-w-full" aria-hidden>
          {ticks.map((t) => (
            <g key={t}>
              <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(t)} y2={y(t)} stroke="var(--color-line)" strokeWidth={1} />
              <text x={MARGIN.left - 8} y={y(t)} dy="0.32em" textAnchor="end" fontSize={11} fill="var(--color-muted)" className="tabular-nums">
                {t.toLocaleString("en-US")}
              </text>
            </g>
          ))}
          <text x={MARGIN.left} y={HEIGHT - 8} fontSize={11} fill="var(--color-muted)">
            Start
          </text>
          <text x={width - MARGIN.right} y={HEIGHT - 8} fontSize={11} fill="var(--color-muted)" textAnchor="end">
            Match {data.length - 1}
          </text>

          <path d={path} fill="none" stroke={accent} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />

          {/* End marker (2px surface ring) + current value in text ink */}
          <circle cx={x(last.index)} cy={y(last.rating)} r={4} fill={accent} stroke="var(--color-surface)" strokeWidth={2} />
          <text x={x(last.index) + 10} y={y(last.rating)} dy="0.32em" fontSize={12} fontWeight={700} fill="var(--color-fg)" className="tabular-nums">
            {last.rating}
          </text>

          {point && (
            <g pointerEvents="none">
              <line x1={x(point.index)} x2={x(point.index)} y1={MARGIN.top} y2={HEIGHT - MARGIN.bottom} stroke="var(--color-faint)" strokeWidth={1} />
              <circle cx={x(point.index)} cy={y(point.rating)} r={4} fill={accent} stroke="var(--color-surface)" strokeWidth={2} />
            </g>
          )}

          {/* Hit area: the whole plot, so the nearest match is found without precise aiming. */}
          <rect
            x={0}
            y={0}
            width={width}
            height={HEIGHT}
            fill="transparent"
            onPointerMove={onPointerMove}
            onPointerLeave={() => setActive(null)}
          />
        </svg>

        {point && (
          <div
            role="status"
            className="pointer-events-none absolute top-1 z-10 -translate-x-1/2 rounded-lg border border-line bg-raised px-3 py-2 text-xs shadow-lg shadow-black/40"
            style={{ left: Math.min(Math.max(x(point.index), 70), width - 70) }}
          >
            <p className="text-sm font-bold text-fg tabular-nums">{point.rating}</p>
            <p className="whitespace-nowrap text-muted">{describe(point)}</p>
          </div>
        )}
      </div>

      <details className="mt-3 text-sm">
        <summary className="cursor-pointer text-xs font-medium text-muted hover:text-fg">Show as table</summary>
        <table className="mt-2 w-full text-left text-xs">
          <thead className="text-faint">
            <tr>
              <th className="py-1 font-medium">Match</th>
              <th className="py-1 font-medium">Date</th>
              <th className="py-1 text-right font-medium">Change</th>
              <th className="py-1 text-right font-medium">Rating</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line text-fg tabular-nums">
            {data.map((d) => (
              <tr key={d.index}>
                <td className="py-1">{d.index === 0 ? "Start" : d.index}</td>
                <td className="py-1 text-muted">{d.at ? formatDate(d.at) : "–"}</td>
                <td className="py-1 text-right">{d.change === null ? "–" : formatChange(d.change)}</td>
                <td className="py-1 text-right font-semibold">{d.rating}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
