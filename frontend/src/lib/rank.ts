/**
 * Display-only rank tiers derived from an Elo rating. Ratings themselves are always computed by
 * the backend; this only decides how a rating is presented.
 */
export interface RankTier {
  name: string;
  minRating: number;
  color: string;
}

export const RANK_TIERS: readonly RankTier[] = [
  { name: "Bronze", minRating: 0, color: "#c98552" },
  { name: "Silver", minRating: 900, color: "#b4bccd" },
  { name: "Gold", minRating: 1100, color: "#f3c24f" },
  { name: "Platinum", minRating: 1300, color: "#5fe0d0" },
  { name: "Diamond", minRating: 1500, color: "#8f7dff" },
  { name: "Champion", minRating: 1700, color: "#ff5d8f" },
];

export function tierForRating(rating: number): RankTier {
  for (let i = RANK_TIERS.length - 1; i >= 0; i--) {
    if (rating >= RANK_TIERS[i].minRating) return RANK_TIERS[i];
  }
  return RANK_TIERS[0];
}
