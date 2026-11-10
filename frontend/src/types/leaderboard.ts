export interface LeaderboardEntry {
  rank: number;
  username: string;
  avatarUrl: string | null;
  rating: number;
  wins: number;
  losses: number;
  draws: number;
  gamesPlayed: number;
}

/** Response of GET /api/leaderboards/{scope}. */
export interface Leaderboard {
  /** "global" or a game slug. */
  scope: string;
  scopeName: string;
  search: string | null;
  page: number;
  size: number;
  total: number;
  entries: LeaderboardEntry[];
  /** The signed-in viewer's own row, if they're ranked on this board. */
  viewer: LeaderboardEntry | null;
}
