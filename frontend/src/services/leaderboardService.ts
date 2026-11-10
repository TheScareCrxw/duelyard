import type { Leaderboard } from "@/types/leaderboard";
import { apiGet, ApiError } from "./apiClient";

export const LEADERBOARD_PAGE_SIZE = 25;

/** A leaderboard page, or null if the board doesn't exist. */
export async function getLeaderboard(
  scope: string,
  options: { search?: string; page?: number; accessToken?: string | null } = {},
): Promise<Leaderboard | null> {
  const query = new URLSearchParams({ page: String(options.page ?? 0), size: String(LEADERBOARD_PAGE_SIZE) });
  if (options.search) query.set("search", options.search);
  try {
    return await apiGet<Leaderboard>(`/api/leaderboards/${encodeURIComponent(scope)}?${query}`, {
      accessToken: options.accessToken ?? undefined,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}
