"use client";

import { useActionState, useState } from "react";
import { updateAvatarAction } from "@/app/settings/actions";
import { FormAlert } from "@/components/auth/FormAlert";
import { SubmitButton } from "@/components/auth/SubmitButton";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { allPresetAvatars, presetKey } from "@/lib/avatars";
import { initialAuthFormState } from "@/types/auth";

const PRESETS = allPresetAvatars().map(presetKey);

export function AvatarPicker({ username, current }: { username: string; current: string | null }) {
  const [state, action] = useActionState(updateAvatarAction, initialAuthFormState);
  const [selected, setSelected] = useState(current ?? "");

  return (
    <form action={action} className="space-y-5">
      {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
      {state.message && <FormAlert tone="success">{state.message}</FormAlert>}

      <div className="flex items-center gap-4">
        <PlayerAvatar username={username} avatarUrl={selected || null} size="lg" />
        <p className="text-sm text-muted">Pick an emblem and color. Photo uploads are coming later.</p>
      </div>

      <fieldset>
        <legend className="sr-only">Avatar</legend>
        <div className="grid grid-cols-8 gap-1.5 sm:gap-2">
          {PRESETS.map((key) => (
            <label key={key} className="group relative cursor-pointer">
              <input
                type="radio"
                name="avatar"
                value={key}
                checked={selected === key}
                onChange={() => setSelected(key)}
                className="peer sr-only"
              />
              <PlayerAvatar
                username={username}
                avatarUrl={key}
                size="md"
                className="!w-full !h-auto aspect-square ring-offset-2 ring-offset-surface transition peer-checked:ring-2 peer-checked:ring-volt peer-focus-visible:ring-2 peer-focus-visible:ring-volt/60 group-hover:brightness-125"
              />
            </label>
          ))}
        </div>
        <label className="mt-3 inline-flex cursor-pointer items-center gap-2 text-sm text-muted">
          <input type="radio" name="avatar" value="" checked={selected === ""} onChange={() => setSelected("")} />
          No avatar (show my initial)
        </label>
      </fieldset>

      <div className="sm:w-48">
        <SubmitButton pendingLabel="Saving…" variant="secondary">
          Save avatar
        </SubmitButton>
      </div>
    </form>
  );
}
