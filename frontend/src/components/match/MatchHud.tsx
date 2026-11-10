"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { buttonClasses, LinkButton } from "@/components/ui/Button";
import type { PlayerSummary, RoomState, SeatResult } from "@/types/realtime";

/** Re-renders every `intervalMs` and returns Date.now(). */
export function useNow(intervalMs = 250) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);
  return now;
}

export function PlayerChip({
  player,
  active,
  you,
  score,
  align = "left",
}: {
  player: PlayerSummary;
  active: boolean;
  you: boolean;
  score?: string;
  align?: "left" | "right";
}) {
  return (
    <div
      className={`flex min-w-0 flex-1 items-center gap-3 rounded-xl border px-3 py-2.5 transition-colors ${
        active ? "border-volt/60 bg-volt/[0.07]" : "border-line bg-surface"
      } ${align === "right" ? "flex-row-reverse text-right" : ""}`}
    >
      <div className="relative shrink-0">
        <PlayerAvatar username={player.username} avatarUrl={player.avatarUrl} size="md" />
        <span
          title={player.connected ? "Connected" : "Disconnected"}
          className={`absolute -right-0.5 -bottom-0.5 h-3 w-3 rounded-full border-2 border-surface ${player.connected ? "bg-win" : "bg-loss"}`}
        />
      </div>
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-semibold">
          {player.username}
          {you && <span className="ml-1.5 text-xs font-medium text-volt">You</span>}
        </p>
        <p className="font-mono text-xs text-muted tabular-nums">{player.rating}</p>
      </div>
      {score !== undefined && <span className="shrink-0 font-display text-2xl font-bold tabular-nums">{score}</span>}
    </div>
  );
}

export function TurnBanner({ room, now }: { room: RoomState; now: number }) {
  if (room.phase === "COUNTDOWN") {
    const left = Math.max(0, Math.ceil((room.startsAt - now) / 1000));
    return <Banner tone="info">Match starts in {left}…</Banner>;
  }
  if (room.phase !== "PLAYING" || room.turnSeat < 0) return null;
  const mine = room.turnSeat === room.yourSeat;
  const left = room.turnDeadline ? Math.max(0, Math.ceil((room.turnDeadline - now) / 1000)) : null;
  const urgent = left !== null && left <= 5;
  return (
    <Banner tone={mine ? "you" : "info"}>
      {mine ? "Your turn" : `${room.players[room.turnSeat].username}'s turn`}
      {left !== null && <span className={`ml-2 font-mono tabular-nums ${urgent ? "text-loss" : "text-muted"}`}>{left}s</span>}
    </Banner>
  );
}

function Banner({ tone, children }: { tone: "you" | "info"; children: React.ReactNode }) {
  return (
    <div
      role="status"
      className={`rounded-lg px-4 py-2 text-center text-sm font-semibold ${tone === "you" ? "bg-volt text-base" : "bg-raised text-fg"}`}
    >
      {children}
    </div>
  );
}

const TITLES: Record<string, string> = { WIN: "Victory", LOSS: "Defeat", DRAW: "Draw" };
const REASONS: Record<string, string> = {
  FORFEIT: "by forfeit",
  DISCONNECT: "Opponent disconnected",
  TIMEOUT: "Too many missed turns",
  ABANDONED: "Match abandoned: both players left. No result recorded.",
};

export function EndOverlay({
  results,
  reason,
  yourSeat,
  game,
  mode,
}: {
  results: SeatResult[];
  reason: string;
  yourSeat: number;
  game: string;
  mode: string;
}) {
  const mine = results.find((r) => r.seat === yourSeat);
  const result = mine?.result ?? null;
  const change = mine?.ratingBefore != null && mine?.ratingAfter != null ? mine.ratingAfter - mine.ratingBefore : null;
  const title = result ? TITLES[result] : "Match over";
  const color = result === "WIN" ? "text-volt" : result === "LOSS" ? "text-loss" : "text-fg";

  return (
    <div className="absolute inset-0 z-20 flex items-center justify-center bg-base/80 p-4 backdrop-blur-sm">
      <div role="dialog" aria-labelledby="result-title" className="w-full max-w-sm rounded-2xl border border-line bg-surface p-6 text-center shadow-2xl">
        <h2 id="result-title" className={`font-display text-4xl font-bold uppercase ${color}`}>
          {title}
        </h2>
        {reason !== "NORMAL" && REASONS[reason] && <p className="mt-2 text-sm text-muted">{REASONS[reason]}</p>}
        {change !== null && (
          <p className="mt-5 font-mono text-lg tabular-nums">
            {mine!.ratingBefore} → <span className="font-bold">{mine!.ratingAfter}</span>{" "}
            <span className={change >= 0 ? "text-win" : "text-loss"}>
              ({change >= 0 ? "+" : "−"}
              {Math.abs(change)})
            </span>
          </p>
        )}
        {mode === "CASUAL" && result && <p className="mt-3 text-xs text-faint">Casual match: ratings unchanged.</p>}
        <div className="mt-6 flex flex-col gap-2">
          <LinkButton href={`/play/${game}?mode=${mode}`}>Play again</LinkButton>
          <Link href="/dashboard" className={buttonClasses("secondary")}>
            Dashboard
          </Link>
        </div>
      </div>
    </div>
  );
}
