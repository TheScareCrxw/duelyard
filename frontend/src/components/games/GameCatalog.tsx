import { connection } from "next/server";
import { GAME_PRESENTATIONS, presentationFor } from "@/games/registry";
import { listGames } from "@/services/gameService";
import type { GameSummary } from "@/types/game";
import { GameCard } from "./GameCard";

/**
 * Server component: loads the game catalog from the API. If the API is unreachable, the cards
 * still render from local presentation data so the landing page never breaks.
 */
export async function GameCatalog() {
  // Always render at request time: game availability is live data, and the API is not
  // necessarily reachable at build time.
  await connection();

  let games: GameSummary[] | null = null;
  try {
    games = await listGames();
  } catch (error) {
    console.error("Failed to load game catalog", error);
  }

  return (
    <>
      {games === null && (
        <p role="status" className="mb-6 rounded-lg border border-line bg-surface px-4 py-3 text-sm text-muted">
          The game server is unreachable right now, so live game status is unavailable.
        </p>
      )}
      <div className="grid gap-5 sm:grid-cols-2">
        {games
          ? games.map((g) => (
              <GameCard
                key={g.slug}
                presentation={presentationFor(g.slug, g.displayName)}
                tagline={g.tagline}
                modes={g.modes}
                availability={g.availability}
              />
            ))
          : GAME_PRESENTATIONS.map((p) => (
              <GameCard key={p.slug} presentation={p} tagline="" modes={["RANKED", "CASUAL"]} availability={null} />
            ))}
      </div>
    </>
  );
}

export function GameCatalogSkeleton() {
  return (
    <div className="grid gap-5 sm:grid-cols-2" aria-busy="true" aria-label="Loading games">
      {GAME_PRESENTATIONS.map((p) => (
        <div key={p.slug} className="h-64 animate-pulse rounded-2xl border border-line bg-surface" />
      ))}
    </div>
  );
}
