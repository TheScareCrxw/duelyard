import { RankBadge } from "@/components/rank/RankBadge";
import { presentationFor } from "@/games/registry";
import type { GameRating } from "@/types/player";

/** A player's rating in one game: tier, current and peak rating, ranked record. */
export function GameRatingCard({ rating }: { rating: GameRating }) {
  const { Icon, accent, displayName } = presentationFor(rating.gameSlug, rating.gameName);
  const ranked = rating.rankedGamesPlayed;

  return (
    <div className="rounded-xl border border-line bg-surface p-5">
      <div className="flex items-center gap-3">
        <Icon className="h-6 w-6 shrink-0" style={{ color: accent }} />
        <span className="truncate font-semibold">{displayName}</span>
      </div>
      <div className="mt-4 flex items-end justify-between gap-2">
        <span className="font-display text-3xl font-bold tabular-nums">{rating.rating}</span>
        <RankBadge rating={rating.rating} tierOnly />
      </div>
      <dl className="mt-4 grid grid-cols-2 gap-2 text-xs">
        <div>
          <dt className="text-faint">Peak</dt>
          <dd className="font-mono text-fg tabular-nums">{rating.peakRating}</dd>
        </div>
        <div>
          <dt className="text-faint">Ranked W–L–D</dt>
          <dd className="font-mono text-fg tabular-nums">
            {ranked === 0 ? "–" : `${rating.rankedWins}–${rating.rankedLosses}–${rating.rankedDraws}`}
          </dd>
        </div>
      </dl>
    </div>
  );
}
