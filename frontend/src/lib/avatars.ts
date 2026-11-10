/**
 * Preset avatars, stored as `preset:<emblem>:<color>`. Must match the backend's AvatarCatalog,
 * which is the authority on which values are accepted.
 */
export const AVATAR_EMBLEMS = ["bolt", "crown", "flame", "orbit", "shield", "star", "target", "wave"] as const;
export const AVATAR_COLORS = ["volt", "ember", "ocean", "jade", "amber", "violet", "rose", "slate"] as const;

export type AvatarEmblem = (typeof AVATAR_EMBLEMS)[number];
export type AvatarColor = (typeof AVATAR_COLORS)[number];

export const AVATAR_COLOR_HEX: Record<AvatarColor, string> = {
  volt: "#c8ff3d",
  ember: "#ff5a36",
  ocean: "#4f8dff",
  jade: "#34d27b",
  amber: "#ffae2b",
  violet: "#8f7dff",
  rose: "#ff5d8f",
  slate: "#b4bccd",
};

export interface PresetAvatar {
  emblem: AvatarEmblem;
  color: AvatarColor;
}

export function presetKey(avatar: PresetAvatar): string {
  return `preset:${avatar.emblem}:${avatar.color}`;
}

export function parsePresetAvatar(value: string | null | undefined): PresetAvatar | null {
  const match = /^preset:([a-z]+):([a-z]+)$/.exec(value ?? "");
  if (!match) return null;
  const [, emblem, color] = match;
  if (!(AVATAR_EMBLEMS as readonly string[]).includes(emblem)) return null;
  if (!(AVATAR_COLORS as readonly string[]).includes(color)) return null;
  return { emblem: emblem as AvatarEmblem, color: color as AvatarColor };
}

/** Every preset, emblem-major, for the picker. */
export function allPresetAvatars(): PresetAvatar[] {
  return AVATAR_EMBLEMS.flatMap((emblem) => AVATAR_COLORS.map((color) => ({ emblem, color })));
}
