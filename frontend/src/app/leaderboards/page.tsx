import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { LeaderboardTable, RankCell } from "@/components/leaderboard/LeaderboardTable";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { buttonClasses } from "@/components/ui/Button";
import { GAME_PRESENTATIONS } from "@/games/registry";
import { getAccessToken } from "@/lib/auth/dal";
import { getLeaderboard, LEADERBOARD_PAGE_SIZE } from "@/services/leaderboardService";

export const metadata: Metadata = { title: "Leaderboards" };

type SearchParams = Promise<Record<string, string | string[] | undefined>>;

const BOARDS = [
  { scope: "global", label: "Overall" },
  ...GAME_PRESENTATIONS.map((g) => ({ scope: g.slug, label: g.displayName })),
];

function href(params: { board: string; q?: string; page?: number }) {
  const query = new URLSearchParams();
  if (params.board !== "global") query.set("board", params.board);
  if (params.q) query.set("q", params.q);
  if (params.page) query.set("page", String(params.page));
  const s = query.toString();
  return s ? `/leaderboards?${s}` : "/leaderboards";
}

export default async function LeaderboardsPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams;
  const one = (k: string) => (typeof params[k] === "string" ? (params[k] as string) : undefined);
  const board = one("board") ?? "global";
  const q = one("q")?.trim().slice(0, 20) || undefined;
  const page = Math.max(0, Number.parseInt(one("page") ?? "0", 10) || 0);

  const data = await getLeaderboard(board, { search: q, page, accessToken: await getAccessToken() });
  if (!data) notFound();

  const first = data.total === 0 ? 0 : page * data.size + 1;
  const last = Math.min(data.total, (page + 1) * data.size);
  const hasNext = last < data.total;
  const viewerOnPage = data.viewer && data.entries.some((e) => e.username === data.viewer!.username);

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6 sm:py-12">
      <h1 className="font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">Leaderboards</h1>
      <p className="mt-2 max-w-2xl text-sm text-muted">
        {board === "global"
          ? "Overall rating is the average of a player's four game ratings (unplayed games count as 1000)."
          : `Ranked ${data.scopeName} ratings.`}{" "}
        Players appear after their first ranked match.
      </p>

      {/* Filters in one row above the board. */}
      <div className="mt-8 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <nav aria-label="Leaderboard" className="-mx-1 flex gap-1 overflow-x-auto px-1 pb-1 [scrollbar-width:none]">
          {BOARDS.map((b) => {
            const active = b.scope === board;
            return (
              <Link
                key={b.scope}
                href={href({ board: b.scope, q })}
                aria-current={active ? "page" : undefined}
                className={`shrink-0 rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                  active ? "bg-raised text-fg" : "text-muted hover:text-fg"
                }`}
              >
                {b.label}
              </Link>
            );
          })}
        </nav>
        <form action="/leaderboards" className="flex gap-2" role="search">
          {board !== "global" && <input type="hidden" name="board" value={board} />}
          <label htmlFor="q" className="sr-only">
            Search players
          </label>
          <input
            id="q"
            name="q"
            defaultValue={q}
            maxLength={20}
            placeholder="Search players"
            autoComplete="off"
            className="h-9 w-full rounded-lg border border-line bg-base px-3 text-sm placeholder:text-faint focus:border-volt focus:outline-none lg:w-56"
          />
          <button type="submit" className={buttonClasses("secondary", "sm")}>
            Search
          </button>
        </form>
      </div>

      {data.viewer && !viewerOnPage && !q && (
        <Link
          href={`/profile/${encodeURIComponent(data.viewer.username)}`}
          className="mt-6 flex items-center gap-3 rounded-xl border border-volt/30 bg-volt/[0.06] px-4 py-3 text-sm hover:border-volt/60"
        >
          <span className="text-muted">Your rank</span>
          <RankCell rank={data.viewer.rank} />
          <PlayerAvatar username={data.viewer.username} avatarUrl={data.viewer.avatarUrl} size="sm" />
          <span className="truncate font-semibold">{data.viewer.username}</span>
          <span className="ml-auto font-display text-lg font-bold tabular-nums">{data.viewer.rating}</span>
        </Link>
      )}

      <div className="mt-6">
        {data.entries.length === 0 ? (
          <div className="rounded-xl border border-dashed border-line px-5 py-14 text-center text-sm text-muted">
            {q ? (
              <>
                No ranked players match &ldquo;{q}&rdquo;.{" "}
                <Link href={href({ board })} className="font-semibold text-volt hover:text-volt-dim">
                  Clear search
                </Link>
              </>
            ) : (
              "Nobody is ranked here yet. Play a ranked match to claim the top spot."
            )}
          </div>
        ) : (
          <LeaderboardTable entries={data.entries} viewerUsername={data.viewer?.username} />
        )}
      </div>

      {data.total > LEADERBOARD_PAGE_SIZE && (
        <div className="mt-4 flex items-center justify-between text-sm">
          <span className="text-muted tabular-nums">
            {first}–{last} of {data.total.toLocaleString("en-US")}
          </span>
          <div className="flex gap-2">
            {page > 0 && (
              <Link href={href({ board, q, page: page - 1 })} className={buttonClasses("secondary", "sm")}>
                Previous
              </Link>
            )}
            {hasNext && (
              <Link href={href({ board, q, page: page + 1 })} className={buttonClasses("secondary", "sm")}>
                Next
              </Link>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
