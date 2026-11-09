import { GameRatingCard } from "@/components/stats/GameRatingCard";
import { MatchHistoryList } from "@/components/stats/MatchHistoryList";
import { RatingHistoryPanel } from "@/components/stats/RatingHistoryPanel";
import { StatTile, formatWinRate } from "@/components/stats/StatTile";
import type { PlayerProfile, RatingHistory } from "@/types/player";

/** Summary tiles, per-game ratings and recent matches. Used by the profile page and dashboard. */
export function ProfileStats({
  profile,
  emptyMatches,
  ratingHistories,
}: {
  profile: PlayerProfile;
  emptyMatches: string;
  /** Omitted when the history couldn't be loaded; the section is then skipped. */
  ratingHistories?: { gameName: string; history: RatingHistory }[];
}) {
  const { summary, ratings, recentMatches } = profile;
  if (!summary || !ratings || !recentMatches) return null;

  return (
    <div className="space-y-10">
      <section aria-labelledby="overview">
        <h2 id="overview" className="sr-only">
          Overview
        </h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatTile label="Games" value={String(summary.gamesPlayed)} />
          <StatTile label="Wins" value={String(summary.wins)} hint={`${summary.losses} losses · ${summary.draws} draws`} />
          <StatTile label="Win rate" value={formatWinRate(summary.winRate)} />
          <StatTile label="Highest rating" value={String(summary.highestRating)} />
        </div>
      </section>

      <section aria-labelledby="ratings">
        <h2 id="ratings" className="text-xs font-semibold tracking-[0.18em] text-muted uppercase">
          Ratings
        </h2>
        <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {ratings.map((r) => (
            <GameRatingCard key={r.gameSlug} rating={r} />
          ))}
        </div>
      </section>

      {ratingHistories && (
        <section aria-labelledby="rating-history">
          <h2 id="rating-history" className="text-xs font-semibold tracking-[0.18em] text-muted uppercase">
            Rating history
          </h2>
          <div className="mt-4">
            <RatingHistoryPanel histories={ratingHistories} />
          </div>
        </section>
      )}

      <section aria-labelledby="recent">
        <h2 id="recent" className="text-xs font-semibold tracking-[0.18em] text-muted uppercase">
          Recent matches
        </h2>
        <div className="mt-4">
          <MatchHistoryList matches={recentMatches} emptyMessage={emptyMatches} />
        </div>
      </section>
    </div>
  );
}
