"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { buttonClasses } from "@/components/ui/Button";
import { useRealtime, useServerMessages } from "@/contexts/RealtimeContext";
import { GAME_RENDERERS } from "@/games/renderers";
import type { EventBatch } from "@/games/types";
import type { RoomState, SeatResult } from "@/types/realtime";
import { ConnectionNotice } from "./ActiveMatchBanner";
import { EndOverlay, PlayerChip, TurnBanner, useNow } from "./MatchHud";

/** A live match: header, turn banner, the game, and the result. All state comes from the server. */
export function MatchView({ matchId }: { matchId: string }) {
  const { client, status } = useRealtime();
  const [room, setRoom] = useState<RoomState | null>(null);
  const [batch, setBatch] = useState<EventBatch | null>(null);
  const [ended, setEnded] = useState<{ reason: string; results: SeatResult[] } | null>(null);
  const [notice, setNoticeState] = useState<{ text: string; at: number } | null>(null);
  const setNotice = (text: string) => setNoticeState({ text, at: Date.now() });
  const [missing, setMissing] = useState(false);
  const batchId = useRef(0);
  const roomRef = useRef<RoomState | null>(null);
  const now = useNow();

  // (Re)sync whenever the connection (re)opens.
  useEffect(() => {
    if (status === "open") client.send({ type: "match.sync", matchId });
  }, [status, client, matchId]);

  useServerMessages((m) => {
    // Every new connection starts with a welcome: re-sync the match on it.
    if (m.type === "welcome") client.send({ type: "match.sync", matchId });
    if (m.type === "match.state" && m.state.matchId === matchId) {
      roomRef.current = m.state;
      setRoom(m.state);
    }
    if (m.type === "match.events" && m.matchId === matchId) {
      const previousView = roomRef.current?.view;
      roomRef.current = m.state;
      setRoom(m.state);
      setBatch({
        id: ++batchId.current,
        actorSeat: m.actorSeat,
        timedOut: m.timedOut,
        events: m.events,
        previousView,
        receivedAt: performance.now(),
        receivedWall: Date.now(),
      });
      if (m.timedOut) setNotice(m.actorSeat === m.state.yourSeat ? "You ran out of time." : "Your opponent ran out of time.");
    }
    if (m.type === "match.ended" && m.matchId === matchId) setEnded({ reason: m.reason, results: m.results });
    if (m.type === "opponent.connection" && m.matchId === matchId) {
      setNotice(m.connected ? "Your opponent is back." : `Your opponent disconnected. They have ${m.graceSeconds}s to return.`);
      client.send({ type: "match.sync", matchId });
    }
    if (m.type === "error") {
      if (m.code === "NO_SUCH_MATCH") setMissing(true);
      else setNotice(m.message);
    }
  });

  if (missing && !room) {
    return (
      <div className="mx-auto max-w-md px-4 py-24 text-center">
        <h1 className="font-display text-3xl font-bold uppercase">Match not found</h1>
        <p className="mt-3 text-muted">It may have finished a while ago.</p>
        <Link href="/play" className={buttonClasses("secondary", "md", "mt-8")}>
          Back to games
        </Link>
      </div>
    );
  }
  if (!room) {
    return (
      <div className="mx-auto max-w-md px-4 py-24 text-center text-muted" role="status">
        <ConnectionNotice />
        Loading match…
      </div>
    );
  }

  const renderer = GAME_RENDERERS[room.game];
  const Game = renderer?.Component;
  const canAct = room.phase === "PLAYING" && room.turnSeat === room.yourSeat && !ended;
  const [left, right] = [room.players[room.yourSeat], room.players[1 - room.yourSeat]];

  return (
    <div className="mx-auto max-w-5xl px-4 py-6 sm:px-6 sm:py-8">
      <ConnectionNotice />
      <div className="flex items-stretch gap-3">
        <PlayerChip player={left} you active={room.turnSeat === left.seat} score={renderer?.score(room.view, left.seat)} />
        <PlayerChip player={right} you={false} active={room.turnSeat === right.seat} score={renderer?.score(room.view, right.seat)} align="right" />
      </div>
      <div className="mt-3">
        <TurnBanner room={room} now={now} />
      </div>
      {notice && now - notice.at < 4000 && (
        <p role="status" className="mt-2 text-center text-sm text-muted">
          {notice.text}
        </p>
      )}
      <div className="relative mt-4 rounded-2xl border border-line bg-surface p-3 sm:p-5">
        {Game ? (
          <Game
            room={room}
            batch={batch}
            canAct={canAct}
            act={(input: Record<string, number>) => client.send({ type: "match.action", matchId, input })}
            now={now}
          />
        ) : (
          <p className="p-8 text-center text-muted">This game can&apos;t be displayed.</p>
        )}
        {ended && (
          <EndOverlay results={ended.results} reason={ended.reason} yourSeat={room.yourSeat} game={room.game} mode={room.mode} />
        )}
      </div>
      <p className="mt-3 text-center text-xs text-faint">
        {room.mode === "RANKED" ? "Ranked match" : "Casual match"} · Every move is checked by the game server.
      </p>
    </div>
  );
}
