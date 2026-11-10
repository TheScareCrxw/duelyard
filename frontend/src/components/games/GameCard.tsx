import type { CSSProperties } from "react";
import type { GamePresentation } from "@/games/registry";
import type { GameAvailability, GameMode } from "@/types/game";

interface GameCardProps {
  presentation: GamePresentation;
  tagline: string;
  modes: GameMode[];
  /** Null when the backend could not be reached and availability is unknown. */
  availability: GameAvailability | null;
}

const MODE_LABEL: Record<GameMode, string> = { RANKED: "Ranked", CASUAL: "Casual" };

export function GameCard({ presentation, tagline, modes, availability }: GameCardProps) {
  const { Icon, displayName, blurb, accent } = presentation;

  return (
    <article
      style={{ "--accent": accent } as CSSProperties}
      className="group relative flex flex-col overflow-hidden rounded-2xl border border-line bg-surface p-6 transition-[border-color,transform] duration-200 hover:-translate-y-0.5 hover:border-[color-mix(in_oklab,var(--accent)_50%,var(--color-line))]"
    >
      <div
        aria-hidden
        className="pointer-events-none absolute -top-24 -right-24 h-56 w-56 rounded-full opacity-20 blur-3xl transition-opacity duration-300 group-hover:opacity-35"
        style={{ background: accent }}
      />

      <div className="relative flex items-start justify-between gap-4">
        <div
          className="flex h-14 w-14 items-center justify-center rounded-xl border"
          style={{
            color: accent,
            borderColor: `color-mix(in oklab, ${accent} 35%, transparent)`,
            background: `color-mix(in oklab, ${accent} 12%, transparent)`,
          }}
        >
          <Icon className="h-8 w-8" />
        </div>
        <AvailabilityTag availability={availability} />
      </div>

      <h3 className="relative mt-5 font-display text-2xl font-bold tracking-wide uppercase">{displayName}</h3>
      <p className="relative mt-1.5 text-sm text-fg/85">{tagline}</p>
      {blurb && <p className="relative mt-3 text-sm text-muted">{blurb}</p>}

      <div className="relative mt-auto flex flex-wrap gap-2 pt-6">
        {modes.map((mode) => (
          <span
            key={mode}
            className="rounded-md border border-line bg-raised px-2 py-1 text-xs font-medium text-muted"
          >
            {MODE_LABEL[mode]}
          </span>
        ))}
      </div>
    </article>
  );
}

function AvailabilityTag({ availability }: { availability: GameAvailability | null }) {
  if (availability === "AVAILABLE") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-win/15 px-2.5 py-1 text-xs font-semibold text-win">
        <span className="h-1.5 w-1.5 rounded-full bg-win" /> Live
      </span>
    );
  }
  if (availability === "COMING_SOON") {
    return <span className="rounded-full bg-raised px-2.5 py-1 text-xs font-semibold text-muted">Coming soon</span>;
  }
  return null;
}
