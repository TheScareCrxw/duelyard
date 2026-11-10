import { Suspense } from "react";
import { GameCatalog, GameCatalogSkeleton } from "@/components/games/GameCatalog";
import { RankBadge } from "@/components/rank/RankBadge";
import { LinkButton } from "@/components/ui/Button";
import { RANK_TIERS } from "@/lib/rank";

export default function LandingPage() {
  return (
    <>
      <Hero />
      <section id="games" className="scroll-mt-20 border-t border-line/70">
        <div className="mx-auto max-w-6xl px-4 py-20 sm:px-6">
          <SectionHeading
            eyebrow="The lineup"
            title="Four games. One ladder each."
            body="Every game keeps its own rating, so your pool skills never drag down your darts rank."
          />
          <div className="mt-10">
            <Suspense fallback={<GameCatalogSkeleton />}>
              <GameCatalog />
            </Suspense>
          </div>
        </div>
      </section>
      <Rankings />
      <FinalCta />
    </>
  );
}

function Hero() {
  return (
    <section className="relative overflow-hidden">
      <div aria-hidden className="bg-grid absolute inset-0" />
      <div
        aria-hidden
        className="absolute top-[-20%] left-1/2 h-[480px] w-[720px] -translate-x-1/2 rounded-full bg-volt/10 blur-[120px]"
      />
      <div className="relative mx-auto grid max-w-6xl items-center gap-14 px-4 pt-16 pb-20 sm:px-6 lg:grid-cols-[1.1fr_1fr] lg:pt-24 lg:pb-28">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-xs font-semibold tracking-wider text-muted uppercase">
            <span className="h-1.5 w-1.5 rounded-full bg-volt" /> 1v1 ranked mini-games
          </p>
          <h1 className="mt-6 font-display text-5xl leading-[1.02] font-bold tracking-tight uppercase sm:text-6xl lg:text-7xl">
            Quick games.
            <br />
            <span className="text-volt">Real rivalries.</span>
          </h1>
          <p className="mt-6 max-w-lg text-lg text-muted">
            Duel real opponents in darts, mini golf, 8-ball pool and rocket artillery. Matches take minutes; your
            rating remembers every one.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <LinkButton href="/register" size="lg">
              Create free account
            </LinkButton>
            <LinkButton href="#games" variant="secondary" size="lg">
              Browse games
            </LinkButton>
          </div>
        </div>
        <MatchPreview />
      </div>
    </section>
  );
}

/** Illustrative post-match card showing how a ranked result moves both ratings. */
function MatchPreview() {
  return (
    <div className="relative mx-auto w-full max-w-md" aria-label="Example ranked match result">
      <div className="rounded-2xl border border-line bg-surface/90 p-5 shadow-2xl shadow-black/50 backdrop-blur">
        <div className="flex items-center justify-between text-xs font-semibold tracking-wider text-muted uppercase">
          <span>Ranked · 8-Ball Pool</span>
          <span className="text-volt">Final</span>
        </div>
        <div className="mt-5 grid grid-cols-[1fr_auto_1fr] items-center gap-3">
          <PlayerSlot name="nightshift" initial="N" rating={1265} delta={17} winner hue="#4f8dff" />
          <span className="font-display text-2xl font-bold text-faint">VS</span>
          <PlayerSlot name="cue_ball_carl" initial="C" rating={1245} delta={-17} hue="#ff4d6a" />
        </div>
        <div className="mt-5 rounded-lg border border-line bg-raised px-4 py-3 text-xs text-muted">
          Underdog win: <span className="text-fg">nightshift</span> was rated 14 lower going in, so the swing is
          bigger than an even match.
        </div>
      </div>
    </div>
  );
}

function PlayerSlot(props: {
  name: string;
  initial: string;
  rating: number;
  delta: number;
  hue: string;
  winner?: boolean;
}) {
  const { name, initial, rating, delta, hue, winner } = props;
  return (
    <div className="flex min-w-0 flex-col items-center gap-2 text-center">
      <div
        className={`flex h-14 w-14 items-center justify-center rounded-full font-display text-xl font-bold ${winner ? "ring-2 ring-volt ring-offset-2 ring-offset-surface" : ""}`}
        style={{ background: `color-mix(in oklab, ${hue} 25%, var(--color-raised))`, color: hue }}
      >
        {initial}
      </div>
      <span className="w-full truncate text-sm font-semibold">{name}</span>
      <RankBadge rating={rating} />
      <span className={`font-mono text-sm font-semibold tabular-nums ${delta > 0 ? "text-win" : "text-loss"}`}>
        {delta > 0 ? `+${delta}` : `−${Math.abs(delta)}`}
      </span>
    </div>
  );
}

function Rankings() {
  return (
    <section id="rankings" className="scroll-mt-20 border-t border-line/70 bg-surface/40">
      <div className="mx-auto grid max-w-6xl gap-12 px-4 py-20 sm:px-6 lg:grid-cols-2">
        <div>
          <SectionHeading
            eyebrow="Competitive rankings"
            title="Elo that respects the upset."
            body="Everyone starts at 1000 in every game. Beat a higher-rated rival and you climb fast; lose to a lower-rated one and it stings. Casual matches never touch your rating."
          />
          <ul className="mt-8 space-y-4 text-sm">
            <Fact title="Server-verified results">
              Outcomes are decided by the game server, never by your browser, so a rating can&apos;t be faked.
            </Fact>
            <Fact title="Fair matchmaking">
              Ranked queues pair you within about 100 rating points, widening gradually the longer you wait.
            </Fact>
            <Fact title="Per-game ladders">
              Separate ratings, leaderboards and peak-rating records for every game.
            </Fact>
          </ul>
        </div>
        <div className="rounded-2xl border border-line bg-surface p-6">
          <h3 className="text-xs font-semibold tracking-wider text-muted uppercase">Rank tiers</h3>
          <ol className="mt-4 divide-y divide-line">
            {[...RANK_TIERS].reverse().map((tier, i, arr) => {
              const next = arr[i - 1];
              const range = next ? `${tier.minRating}–${next.minRating - 1}` : `${tier.minRating}+`;
              return (
                <li key={tier.name} className="flex items-center justify-between py-3">
                  <RankBadge rating={Math.max(tier.minRating, 1)} tierOnly />
                  <span className="font-mono text-sm text-muted tabular-nums">{range}</span>
                </li>
              );
            })}
          </ol>
        </div>
      </div>
    </section>
  );
}

function Fact({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <li className="flex gap-3">
      <span aria-hidden className="mt-1.5 h-2 w-2 shrink-0 rotate-45 bg-volt" />
      <div>
        <p className="font-semibold text-fg">{title}</p>
        <p className="mt-0.5 text-muted">{children}</p>
      </div>
    </li>
  );
}

function FinalCta() {
  return (
    <section className="border-t border-line/70">
      <div className="mx-auto flex max-w-6xl flex-col items-start gap-6 px-4 py-20 sm:px-6 md:flex-row md:items-center md:justify-between">
        <div>
          <h2 className="font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">Your rival is queueing.</h2>
          <p className="mt-2 text-muted">Sign up with email or Google. It takes about thirty seconds.</p>
        </div>
        <div className="flex gap-3">
          <LinkButton href="/register" size="lg">
            Register
          </LinkButton>
          <LinkButton href="/login" variant="secondary" size="lg">
            Sign in
          </LinkButton>
        </div>
      </div>
    </section>
  );
}

function SectionHeading({ eyebrow, title, body }: { eyebrow: string; title: string; body: string }) {
  return (
    <div className="max-w-2xl">
      <p className="text-xs font-semibold tracking-[0.18em] text-volt uppercase">{eyebrow}</p>
      <h2 className="mt-3 font-display text-3xl font-bold tracking-tight uppercase sm:text-4xl">{title}</h2>
      <p className="mt-4 text-muted">{body}</p>
    </div>
  );
}
