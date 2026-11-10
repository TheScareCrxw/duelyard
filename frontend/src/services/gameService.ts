import type { GameSummary } from "@/types/game";
import { apiGet } from "./apiClient";

export function listGames(): Promise<GameSummary[]> {
  return apiGet<GameSummary[]>("/api/games");
}

export function getGame(slug: string): Promise<GameSummary> {
  return apiGet<GameSummary>(`/api/games/${encodeURIComponent(slug)}`);
}
