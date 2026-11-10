"use client";

import Link from "next/link";
import { useRealtime } from "@/contexts/RealtimeContext";

/** Shown on the play pages when the server says this user is still in a match (e.g. after a refresh). */
export function ActiveMatchBanner() {
  const { activeMatchId } = useRealtime();
  if (!activeMatchId) return null;
  return (
    <Link
      href={`/match/${activeMatchId}`}
      className="mb-6 flex items-center justify-between rounded-xl border border-volt/40 bg-volt/[0.07] px-4 py-3 text-sm hover:border-volt"
    >
      <span className="font-semibold">You have a match in progress</span>
      <span className="text-volt">Return to match →</span>
    </Link>
  );
}

export function ConnectionNotice() {
  const { status } = useRealtime();
  if (status === "open" || status === "connecting") return null;
  const text =
    status === "reconnecting"
      ? "Reconnecting to the game server…"
      : status === "unauthenticated"
        ? "Your session expired. Sign in again to play."
        : "Disconnected. This account may be playing in another tab.";
  return (
    <p role="status" className="mb-4 rounded-lg border border-line bg-raised px-4 py-2 text-sm text-muted">
      {text}
    </p>
  );
}
