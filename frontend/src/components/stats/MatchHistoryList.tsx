import Link from "next/link";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { presentationFor } from "@/games/registry";
import type { MatchResult, MatchSummary } from "@/types/player";

const RESULT_STYLE: Record<MatchResult, { label: string; className: string }> = {
  WIN: { label: "Win", className: "bg-win/15 text-win" },
  LOSS: { label: "Loss", className: "bg-loss/15 text-loss" },
  DRAW: { label: "Draw", className: "bg-raised text-muted" },
};

function timeAgo(iso: string, now = Date.now()): string {
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  const units: [number, string][] = [
    [86400 * 365, "y"],
    [86400 * 30, "mo"],
    [86400, "d"],
    [3600, "h"],
    [60, "m"],
  ];
  for (const [size, unit] of units) {
    if (seconds >= size) return `${Math.floor(seconds / size)}${unit} ago`;
  }
  return "just now";
}

export function MatchHistoryList({ matches, emptyMessage }: { matches: MatchSummary[]; emptyMessage: string }) {
  if (matches.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-line px-5 py-10 text-center text-sm text-muted">
        {emptyMessage}
      </div>
    );
  }

  return (
    <ul className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-surface">
      {matches.map((m) => {
        const { Icon, accent, displayName } = presentationFor(m.gameSlug, m.gameName);
        const result = m.result ? RESULT_STYLE[m.result] : { label: "Abandoned", className: "bg-raised text-faint" };
        const opponent = m.opponents[0];
        return (
          <li key={m.matchId} className="flex items-center gap-3 px-4 py-3 sm:gap-4">
            <Icon className="h-5 w-5 shrink-0" style={{ color: accent }} aria-label={displayName} />
            <span className={`w-20 shrink-0 rounded-md px-2 py-1 text-center text-xs font-semibold ${result.className}`}>
              {result.label}
            </span>
            <div className="flex min-w-0 flex-1 items-center gap-2">
              {opponent ? (
                <>
                  <span className="hidden text-sm text-faint sm:inline">vs</span>
                  <PlayerAvatar username={opponent.username} avatarUrl={opponent.avatarUrl} size="sm" />
                  <Link
                    href={`/profile/${encodeURIComponent(opponent.username)}`}
                    className="truncate text-sm font-semibold hover:text-volt"
                  >
                    {opponent.username}
                  </Link>
                </>
              ) : (
                <span className="text-sm text-faint">{displayName}</span>
              )}
            </div>
            <span className="hidden text-xs text-faint sm:inline">{m.mode === "RANKED" ? "Ranked" : "Casual"}</span>
            <span
              className={`w-12 shrink-0 text-right font-mono text-sm tabular-nums ${
                m.ratingChange === null ? "text-faint" : m.ratingChange >= 0 ? "text-win" : "text-loss"
              }`}
            >
              {m.ratingChange === null ? "–" : m.ratingChange >= 0 ? `+${m.ratingChange}` : `−${Math.abs(m.ratingChange)}`}
            </span>
            <time dateTime={m.endedAt} className="hidden w-16 shrink-0 text-right text-xs text-faint md:inline">
              {timeAgo(m.endedAt)}
            </time>
          </li>
        );
      })}
    </ul>
  );
}
