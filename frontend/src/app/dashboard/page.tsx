import type { Metadata } from "next";
import { RankBadge } from "@/components/rank/RankBadge";
import { LinkButton } from "@/components/ui/Button";
import { GAME_PRESENTATIONS } from "@/games/registry";
import { requireUser } from "@/lib/auth/dal";

export const metadata: Metadata = { title: "Dashboard" };

/**
 * Protected page. Ratings, match history and quick play arrive in later phases; for now this
 * confirms the session and the account the backend provisioned.
 */
export default async function DashboardPage() {
  const user = await requireUser("/dashboard");
  const joined = new Date(user.createdAt).toLocaleDateString("en-US", { month: "long", year: "numeric" });

  return (
    <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6">
      <div className="flex flex-col gap-6 rounded-2xl border border-line bg-surface p-6 sm:flex-row sm:items-center sm:p-8">
        <div className="flex h-16 w-16 shrink-0 items-center justify-center rounded-full bg-volt/15 font-display text-2xl font-bold text-volt uppercase">
          {user.username.slice(0, 1)}
        </div>
        <div className="min-w-0">
          <p className="text-sm text-muted">Welcome back,</p>
          <h1 className="truncate font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">{user.username}</h1>
          <p className="mt-1 text-sm text-faint">Member since {joined}</p>
        </div>
      </div>

      <h2 className="mt-12 text-xs font-semibold tracking-[0.18em] text-muted uppercase">Your ratings</h2>
      <div className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {GAME_PRESENTATIONS.map(({ slug, displayName, Icon, accent }) => (
          <div key={slug} className="rounded-xl border border-line bg-surface p-5">
            <div className="flex items-center gap-3">
              <Icon className="h-6 w-6" style={{ color: accent }} />
              <span className="font-semibold">{displayName}</span>
            </div>
            <div className="mt-4">
              <RankBadge rating={1000} />
            </div>
          </div>
        ))}
      </div>
      <p className="mt-4 text-sm text-faint">Everyone starts at 1000. Ranked play opens soon.</p>

      <div className="mt-10">
        <LinkButton href="/#games" variant="secondary">
          Browse games
        </LinkButton>
      </div>
    </div>
  );
}
