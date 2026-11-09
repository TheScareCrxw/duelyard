import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";
import { AVATAR_COLORS, AVATAR_EMBLEMS, allPresetAvatars, parsePresetAvatar, presetKey } from "./avatars";

describe("avatars", () => {
  it("round-trips every preset", () => {
    const all = allPresetAvatars();
    expect(all).toHaveLength(64);
    for (const avatar of all) expect(parsePresetAvatar(presetKey(avatar))).toEqual(avatar);
  });

  it("rejects anything that isn't a known preset", () => {
    expect(parsePresetAvatar("preset:bolt:neon")).toBeNull();
    expect(parsePresetAvatar("preset:skull:volt")).toBeNull();
    expect(parsePresetAvatar("https://example.com/a.png")).toBeNull();
    expect(parsePresetAvatar(null)).toBeNull();
  });

  it("matches the backend AvatarCatalog exactly", () => {
    const java = readFileSync(
      fileURLToPath(new URL("../../../backend/src/main/java/com/duelyard/user/AvatarCatalog.java", import.meta.url)),
      "utf8",
    );
    const list = (name: string) =>
      [...(new RegExp(`${name}\\s*=\\s*List\\.of\\(([^)]*)\\)`).exec(java)?.[1] ?? "").matchAll(/"([a-z]+)"/g)].map((m) => m[1]);
    expect(list("EMBLEMS")).toEqual([...AVATAR_EMBLEMS]);
    expect(list("COLORS")).toEqual([...AVATAR_COLORS]);
  });
});
