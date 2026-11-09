import { tierForRating } from "@/lib/rank";

interface RankBadgeProps {
  rating: number;
  /** Hide the numeric rating and show only the tier. */
  tierOnly?: boolean;
  className?: string;
}

/** Compact tier + rating indicator used on cards, profiles and leaderboards. */
export function RankBadge({ rating, tierOnly = false, className = "" }: RankBadgeProps) {
  const tier = tierForRating(rating);
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-xs font-semibold ${className}`}
      style={{
        color: tier.color,
        borderColor: `color-mix(in oklab, ${tier.color} 35%, transparent)`,
        background: `color-mix(in oklab, ${tier.color} 10%, transparent)`,
      }}
    >
      <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden>
        <path d="M6 0.5 11 3.5v5L6 11.5 1 8.5v-5z" fill="currentColor" />
      </svg>
      <span>{tier.name}</span>
      {!tierOnly && <span className="font-mono text-fg/90 tabular-nums">{rating}</span>}
    </span>
  );
}
