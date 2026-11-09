import type { MatchHistoryPage, PlayerProfile } from "@/types/player";
import { apiGet, ApiError } from "./apiClient";

/** A public profile, or null if no such player. Pass the viewer's token so owners see private profiles. */
export async function getPlayerProfile(username: string, accessToken?: string | null): Promise<PlayerProfile | null> {
  try {
    return await apiGet<PlayerProfile>(`/api/players/${encodeURIComponent(username)}`, {
      accessToken: accessToken ?? undefined,
    });
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  }
}

export function getMatchHistory(
  username: string,
  page: number,
  size: number,
  accessToken?: string | null,
): Promise<MatchHistoryPage> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return apiGet<MatchHistoryPage>(`/api/players/${encodeURIComponent(username)}/matches?${query}`, {
    accessToken: accessToken ?? undefined,
  });
}
