"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { buttonClasses } from "@/components/ui/Button";
import { useRealtime, useServerMessages } from "@/contexts/RealtimeContext";
import type { GameMode } from "@/types/game";
import type { PlayerSummary } from "@/types/realtime";
import { ConnectionNotice } from "./ActiveMatchBanner";

type Phase = { kind: "searching"; waited: number; range: number | null } | { kind: "found"; opponent: PlayerSummary } | { kind: "error"; code: string; message: string };

/** Joins the queue on connect (and again after a reconnect), shows progress, and moves to the match when paired. */
export function Matchmaking({ game, gameName, mode }: { game: string; gameName: string; mode: GameMode }) {
  const router = useRouter();
  const { client, status, activeMatchId } = useRealtime();
  const [phase, setPhase] = useState<Phase>({ kind: "searching", waited: 0, range: mode === "RANKED" ? 100 : null });
  const leaving = useRef(false);

  useEffect(() => {
    if (activeMatchId) {
      router.replace(`/match/${activeMatchId}`);
      return;
    }
    if (status === "open") client.send({ type: "queue.join", game, mode });
  }, [status, activeMatchId, client, game, mode, router]);

  // Leaving the page cancels the search.
  useEffect(() => () => void client.send({ type: "queue.leave" }), [client]);

  useServerMessages((m) => {
    // Every new connection starts with a welcome: (re)join the queue on it.
    if (m.type === "welcome" && !m.activeMatchId && !leaving.current) client.send({ type: "queue.join", game, mode });
    if (m.type === "queue.status") setPhase({ kind: "searching", waited: m.waitedSeconds, range: m.ratingRange });
    if (m.type === "match.found") {
      setPhase({ kind: "found", opponent: m.opponent });
      setTimeout(() => router.push(`/match/${m.matchId}`), 1200);
    }
    if (m.type === "error" && m.code !== "NOT_YOUR_TURN") setPhase({ kind: "error", code: m.code, message: m.message });
    if (m.type === "queue.left" && !leaving.current && m.reason !== "CANCELLED") {
      setPhase({ kind: "error", code: m.reason, message: "You left the queue." });
    }
  });

  const cancel = () => {
    leaving.current = true;
    client.send({ type: "queue.leave" });
    router.push("/play");
  };

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-16 text-center">
      <ConnectionNotice />
      <p className="text-xs font-semibold tracking-[0.18em] text-volt uppercase">{mode === "RANKED" ? "Ranked" : "Casual"}</p>
      <h1 className="mt-2 font-display text-4xl font-bold tracking-tight uppercase">{gameName}</h1>

      {phase.kind === "searching" && (
        <>
          <div className="relative mt-12 h-28 w-28" aria-hidden>
            <span className="absolute inset-0 animate-ping rounded-full border-2 border-volt/40" />
            <span className="absolute inset-4 rounded-full border-2 border-volt/70" />
            <span className="absolute inset-[42%] rounded-full bg-volt" />
          </div>
          <p role="status" className="mt-8 text-lg font-semibold">
            Finding an opponent…
          </p>
          <p className="mt-1 font-mono text-sm text-muted tabular-nums">
            {Math.floor(phase.waited / 60)}:{String(phase.waited % 60).padStart(2, "0")}
            {phase.range !== null && <> · ±{phase.range} rating</>}
          </p>
          <button type="button" onClick={cancel} className={buttonClasses("secondary", "md", "mt-10")}>
            Cancel
          </button>
        </>
      )}

      {phase.kind === "found" && (
        <div role="status" className="mt-12 w-full rounded-2xl border border-volt/40 bg-volt/[0.06] p-6">
          <p className="text-sm text-muted">Opponent found</p>
          <div className="mt-4 flex items-center justify-center gap-3">
            <PlayerAvatar username={phase.opponent.username} avatarUrl={phase.opponent.avatarUrl} size="lg" />
            <div className="text-left">
              <p className="font-display text-2xl font-bold">{phase.opponent.username}</p>
              <p className="font-mono text-sm text-muted">{phase.opponent.rating}</p>
            </div>
          </div>
          <p className="mt-4 text-sm text-volt">Get ready…</p>
        </div>
      )}

      {phase.kind === "error" && (
        <div role="alert" className="mt-12 w-full rounded-xl border border-loss/40 bg-loss/10 p-5 text-sm">
          <p className="font-semibold text-loss">{phase.message}</p>
          <Link href="/play" className={buttonClasses("secondary", "sm", "mt-4")}>
            Back to games
          </Link>
        </div>
      )}
    </div>
  );
}
