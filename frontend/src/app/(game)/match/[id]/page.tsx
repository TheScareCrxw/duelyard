import type { Metadata } from "next";
import { MatchView } from "@/components/match/MatchView";
import { requireUser } from "@/lib/auth/dal";

export const metadata: Metadata = { title: "Match" };

export default async function MatchPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  await requireUser(`/match/${id}`);
  return <MatchView matchId={id} />;
}
