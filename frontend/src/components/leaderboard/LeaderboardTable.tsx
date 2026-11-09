import Link from "next/link";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { RankBadge } from "@/components/rank/RankBadge";
import type { LeaderboardEntry } from "@/types/leaderboard";

/** Podium ranks get a subtle tinted chip; every other rank is plain text. */
const PODIUM: Record<number, string> = {
  1: "border-[#f3c24f]/50 bg-[#f3c24f]/10 text-[#f3c24f]",
  2: "border-[#b4bccd]/50 bg-[#b4bccd]/10 text-[#b4bccd]",
  3: "border-[#c98552]/50 bg-[#c98552]/10 text-[#c98552]",
};

export function RankCell({ rank }: { rank: number }) {
  const podium = PODIUM[rank];
  return podium ? (
    <span className={`inline-flex h-7 min-w-7 items-center justify-center rounded-md border px-1.5 font-display text-sm font-bold ${podium}`}>
      {rank}
    </span>
  ) : (
    <span className="inline-block min-w-7 text-center font-mono text-sm text-muted tabular-nums">{rank}</span>
  );
}

function record(e: LeaderboardEntry) {
  return e.draws > 0 ? `${e.wins}–${e.losses}–${e.draws}` : `${e.wins}–${e.losses}`;
}

export function LeaderboardRow({ entry, highlight = false }: { entry: LeaderboardEntry; highlight?: boolean }) {
  return (
    <tr className={highlight ? "bg-volt/[0.06]" : "transition-colors hover:bg-raised/60"}>
      <td className="w-14 py-3 pr-2 pl-4 text-center">
        <RankCell rank={entry.rank} />
      </td>
      <td className="max-w-0 py-3 pr-3">
        <Link href={`/profile/${encodeURIComponent(entry.username)}`} className="group flex min-w-0 items-center gap-3">
          <PlayerAvatar username={entry.username} avatarUrl={entry.avatarUrl} size="sm" />
          <span className="truncate font-semibold group-hover:text-volt">{entry.username}</span>
          {highlight && <span className="shrink-0 text-xs font-medium text-volt">You</span>}
        </Link>
      </td>
      <td className="py-3 pr-3 text-right">
        <span className="font-display text-lg font-bold tabular-nums">{entry.rating}</span>
      </td>
      <td className="hidden py-3 pr-3 sm:table-cell">
        <RankBadge rating={entry.rating} tierOnly />
      </td>
      <td className="hidden py-3 pr-3 text-right font-mono text-sm text-muted tabular-nums md:table-cell">{record(entry)}</td>
      <td className="hidden py-3 pr-4 text-right font-mono text-sm text-muted tabular-nums sm:table-cell">{entry.gamesPlayed}</td>
    </tr>
  );
}

export function LeaderboardTable({ entries, viewerUsername }: { entries: LeaderboardEntry[]; viewerUsername?: string }) {
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-surface">
      <table className="w-full table-fixed text-left">
        <thead className="border-b border-line text-xs font-semibold tracking-wider text-faint uppercase">
          <tr>
            <th scope="col" className="w-14 py-3 pl-4 text-center font-semibold">#</th>
            <th scope="col" className="py-3 font-semibold">Player</th>
            <th scope="col" className="w-20 py-3 pr-3 text-right font-semibold">Rating</th>
            <th scope="col" className="hidden w-32 py-3 font-semibold sm:table-cell">Tier</th>
            <th scope="col" className="hidden w-24 py-3 pr-3 text-right font-semibold md:table-cell" title="Ranked wins–losses(–draws)">Record</th>
            <th scope="col" className="hidden w-20 py-3 pr-4 text-right font-semibold sm:table-cell">Games</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {entries.map((e) => (
            <LeaderboardRow key={e.username} entry={e} highlight={e.username === viewerUsername} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
