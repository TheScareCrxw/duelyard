import type { PlayerProfile, RatingHistory } from "@/types/player";
import { getRatingHistory } from "./playerService";

/**
 * Rating history for every game on a profile, fetched in parallel. Returns undefined if any
 * request fails, so the page renders without the chart rather than failing.
 */
export async function loadRatingHistories(
  profile: PlayerProfile,
  accessToken?: string | null,
): Promise<{ gameName: string; history: RatingHistory }[] | undefined> {
  if (profile.restricted || !profile.ratings) return undefined;
  try {
    return await Promise.all(
      profile.ratings.map(async (r) => ({
        gameName: r.gameName,
        history: await getRatingHistory(profile.username, r.gameSlug, accessToken),
      })),
    );
  } catch (error) {
    console.error("Failed to load rating history", error);
    return undefined;
  }
}
