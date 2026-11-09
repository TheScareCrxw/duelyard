import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { Matchmaking } from "@/components/match/Matchmaking";
import { requireUser } from "@/lib/auth/dal";
import { getGame } from "@/services/gameService";
import { ApiError } from "@/services/apiClient";

export const metadata: Metadata = { title: "Finding a match" };

type Props = { params: Promise<{ game: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> };

export default async function MatchmakingPage({ params, searchParams }: Props) {
  const { game } = await params;
  const mode = (await searchParams).mode === "CASUAL" ? "CASUAL" : "RANKED";
  await requireUser(`/play/${game}?mode=${mode}`);
  const definition = await getGame(game).catch((e) => {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  });
  if (!definition) notFound();
  return <Matchmaking game={definition.slug} gameName={definition.displayName} mode={mode} />;
}
