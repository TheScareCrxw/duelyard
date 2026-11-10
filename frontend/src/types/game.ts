/** Mirrors com.duelyard.game.GameMode on the backend. */
export type GameMode = "RANKED" | "CASUAL";

/** Mirrors com.duelyard.game.GameAvailability on the backend. */
export type GameAvailability = "AVAILABLE" | "COMING_SOON";

/** Response shape of GET /api/games and GET /api/games/{slug}. */
export interface GameSummary {
  slug: string;
  displayName: string;
  tagline: string;
  minPlayers: number;
  maxPlayers: number;
  modes: GameMode[];
  availability: GameAvailability;
}
