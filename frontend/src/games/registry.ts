import type { ComponentType, SVGProps } from "react";
import { DartsIcon, GenericGameIcon, MiniGolfIcon, PoolIcon, RocketIcon } from "./icons";

/**
 * Client-side presentation for each game, keyed by the backend slug. Gameplay code for each game
 * will live in its own folder under src/games/<slug>/ (e.g. Phaser scenes) from Phase 9 onward.
 *
 * The backend is the source of truth for which games exist. A game the backend returns without an
 * entry here still renders, with a neutral fallback style.
 */
export interface GamePresentation {
  slug: string;
  displayName: string;
  accent: string;
  Icon: ComponentType<SVGProps<SVGSVGElement>>;
  /** Short how-it-plays line shown on cards. */
  blurb: string;
}

export const GAME_PRESENTATIONS: readonly GamePresentation[] = [
  {
    slug: "darts",
    displayName: "Darts",
    accent: "var(--color-darts)",
    Icon: DartsIcon,
    blurb: "Turn-based 501. Three darts per turn, finish on a double.",
  },
  {
    slug: "mini-golf",
    displayName: "Mini Golf",
    accent: "var(--color-golf)",
    Icon: MiniGolfIcon,
    blurb: "Physics-driven holes with ramps, walls and moving hazards.",
  },
  {
    slug: "eight-ball",
    displayName: "8-Ball Pool",
    accent: "var(--color-pool)",
    Icon: PoolIcon,
    blurb: "Solids or stripes. Clear your group, then sink the eight.",
  },
  {
    slug: "rocket-launcher",
    displayName: "Rocket Launcher",
    accent: "var(--color-rocket)",
    Icon: RocketIcon,
    blurb: "Artillery duel over destructible terrain. Last one standing.",
  },
];

const BY_SLUG = new Map(GAME_PRESENTATIONS.map((p) => [p.slug, p]));

export function presentationFor(slug: string, displayName = slug): GamePresentation {
  return (
    BY_SLUG.get(slug) ?? {
      slug,
      displayName,
      accent: "var(--color-volt)",
      Icon: GenericGameIcon,
      blurb: "",
    }
  );
}
