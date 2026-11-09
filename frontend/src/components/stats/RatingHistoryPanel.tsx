"use client";

import { useState } from "react";
import { presentationFor } from "@/games/registry";
import type { RatingHistory } from "@/types/player";
import { RatingHistoryChart } from "./RatingHistoryChart";

/**
 * Rating history with one chart per game, chosen by the tabs above it. One game at a time: the
 * game accents are identity colors for single lines, not a palette for comparing lines.
 */
export function RatingHistoryPanel({ histories }: { histories: { gameName: string; history: RatingHistory }[] }) {
  const played = histories.filter((h) => h.history.points.length > 0);
  const mostPlayed = [...played].sort((a, b) => b.history.points.length - a.history.points.length)[0];
  const [selected, setSelected] = useState(mostPlayed?.history.gameSlug);

  if (!mostPlayed) {
    return (
      <div className="rounded-xl border border-dashed border-line px-5 py-10 text-center text-sm text-muted">
        Rating history appears after the first ranked match.
      </div>
    );
  }

  const current = played.find((h) => h.history.gameSlug === selected) ?? mostPlayed;
  const { accent } = presentationFor(current.history.gameSlug, current.gameName);

  return (
    <div className="rounded-xl border border-line bg-surface p-4 sm:p-5">
      <div role="tablist" aria-label="Game" className="flex flex-wrap gap-1.5">
        {played.map(({ gameName, history }) => {
          const { Icon, accent: tabAccent } = presentationFor(history.gameSlug, gameName);
          const isSelected = history.gameSlug === current.history.gameSlug;
          return (
            <button
              key={history.gameSlug}
              type="button"
              role="tab"
              aria-selected={isSelected}
              onClick={() => setSelected(history.gameSlug)}
              className={`inline-flex items-center gap-2 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                isSelected ? "bg-raised text-fg" : "text-muted hover:text-fg"
              }`}
            >
              <Icon className="h-4 w-4" style={{ color: tabAccent }} />
              {gameName}
            </button>
          );
        })}
      </div>
      <h3 className="mt-4 text-sm font-semibold">{current.gameName} rating</h3>
      <div className="mt-2 overflow-hidden" role="tabpanel">
        <RatingHistoryChart key={current.history.gameSlug} history={current.history} accent={accent} gameName={current.gameName} />
      </div>
    </div>
  );
}
