import type { ReactNode } from "react";
import { AVATAR_COLOR_HEX, parsePresetAvatar, type AvatarEmblem } from "@/lib/avatars";

const SIZES = { sm: "h-8 w-8 text-sm", md: "h-12 w-12 text-lg", lg: "h-20 w-20 text-3xl", xl: "h-28 w-28 text-4xl" };

/** Original emblem artwork for preset avatars, drawn on a 32x32 grid. */
const EMBLEMS: Record<AvatarEmblem, ReactNode> = {
  bolt: <path d="M18 4 8 18h7l-2 10 11-15h-7l1-9z" />,
  crown: <path d="M6 22 4 10l7 5 5-8 5 8 7-5-2 12H6zm0 3h20v3H6z" />,
  flame: <path d="M16 3c1 6 9 8 9 16a9 9 0 0 1-18 0c0-4 2-7 4-9 0 3 1 5 3 6-1-5 0-9 2-13z" />,
  orbit: (
    <>
      <circle cx="16" cy="16" r="5" />
      <path
        d="M16 5c7 0 13 5 13 11s-6 11-13 11S3 22 3 16 9 5 16 5zm0 3C10.5 8 6 11.6 6 16s4.5 8 10 8 10-3.6 10-8-4.5-8-10-8z"
        fillRule="evenodd"
      />
    </>
  ),
  shield: <path d="M16 3 27 7v8c0 7-5 12-11 14C10 27 5 22 5 15V7l11-4z" />,
  star: <path d="m16 3 3.8 8 8.7 1.1-6.4 6 1.7 8.7L16 22.6l-7.8 4.2 1.7-8.7-6.4-6L12.2 11z" />,
  target: (
    <path
      d="M16 3a13 13 0 1 1 0 26 13 13 0 0 1 0-26zm0 4a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 4a5 5 0 1 1 0 10 5 5 0 0 1 0-10z"
      fillRule="evenodd"
    />
  ),
  wave: <path d="M3 13c4-5 9-5 13 0s9 5 13 0v6c-4 5-9 5-13 0s-9-5-13 0v-6zm0 9c4-5 9-5 13 0s9 5 13 0v4c-4 5-9 5-13 0s-9-5-13 0v-4z" />,
};

interface PlayerAvatarProps {
  username: string;
  avatarUrl?: string | null;
  size?: keyof typeof SIZES;
  className?: string;
}

/** A player's avatar: preset emblem, or the first letter of their username as a fallback. */
export function PlayerAvatar({ username, avatarUrl, size = "md", className = "" }: PlayerAvatarProps) {
  const preset = parsePresetAvatar(avatarUrl);
  const base = `inline-flex shrink-0 items-center justify-center rounded-full ${SIZES[size]} ${className}`;

  if (preset) {
    const hex = AVATAR_COLOR_HEX[preset.color];
    return (
      <span
        role="img"
        aria-label={`${username}'s avatar`}
        className={base}
        style={{ background: `color-mix(in oklab, ${hex} 18%, var(--color-raised))`, color: hex }}
      >
        <svg viewBox="0 0 32 32" className="h-[58%] w-[58%]" fill="currentColor" aria-hidden>
          {EMBLEMS[preset.emblem]}
        </svg>
      </span>
    );
  }

  return (
    <span aria-hidden className={`${base} bg-volt/15 font-display font-bold text-volt uppercase`}>
      {username.slice(0, 1)}
    </span>
  );
}
