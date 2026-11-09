import type { GameMode } from "./game";
import type { ProfileVisibility } from "./user";

export type MatchResult = "WIN" | "LOSS" | "DRAW";

/** A player's rating and record in one game. */
export interface GameRating {
  gameSlug: string;
  gameName: string;
  rating: number;
  peakRating: number;
  rankedWins: number;
  rankedLosses: number;
  rankedDraws: number;
  rankedGamesPlayed: number;
  casualWins: number;
  casualLosses: number;
  casualDraws: number;
}

export interface MatchOpponent {
  username: string;
  avatarUrl: string | null;
  result: MatchResult | null;
  score: number | null;
}

/** One finished match from a player's point of view. */
export interface MatchSummary {
  matchId: string;
  gameSlug: string;
  gameName: string;
  mode: GameMode;
  status: "COMPLETED" | "ABANDONED";
  endReason: string | null;
  result: MatchResult | null;
  score: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  /** Null for casual or unrated matches. */
  ratingChange: number | null;
  endedAt: string;
  opponents: MatchOpponent[];
}

export interface ProfileSummary {
  gamesPlayed: number;
  wins: number;
  losses: number;
  draws: number;
  /** 0..1, or null before the first game. */
  winRate: number | null;
  highestRating: number;
}

/** Response of GET /api/players/{username}. */
export interface PlayerProfile {
  username: string;
  avatarUrl: string | null;
  memberSince: string;
  profileVisibility: ProfileVisibility;
  self: boolean;
  /** Private profile viewed by someone else: the fields below are null. */
  restricted: boolean;
  summary: ProfileSummary | null;
  ratings: GameRating[] | null;
  recentMatches: MatchSummary[] | null;
}

/** Response of GET /api/players/{username}/matches. */
export interface MatchHistoryPage {
  items: MatchSummary[];
  page: number;
  size: number;
  hasMore: boolean;
}
