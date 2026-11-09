"use client";

import { useActionState } from "react";
import { updateVisibilityAction } from "@/app/settings/actions";
import { FormAlert } from "@/components/auth/FormAlert";
import { SubmitButton } from "@/components/auth/SubmitButton";
import { initialAuthFormState } from "@/types/auth";
import type { ProfileVisibility } from "@/types/user";

const OPTIONS: { value: ProfileVisibility; title: string; body: string }[] = [
  { value: "PUBLIC", title: "Public", body: "Anyone can see your ratings, record and match history." },
  { value: "PRIVATE", title: "Private", body: "Others see only your username and avatar. You still appear on leaderboards." },
];

export function VisibilityForm({ current }: { current: ProfileVisibility }) {
  const [state, action] = useActionState(updateVisibilityAction, initialAuthFormState);

  return (
    <form action={action} className="space-y-4">
      {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
      {state.message && <FormAlert tone="success">{state.message}</FormAlert>}
      <fieldset className="grid gap-3 sm:grid-cols-2">
        <legend className="sr-only">Profile visibility</legend>
        {OPTIONS.map((o) => (
          <label
            key={o.value}
            className="flex cursor-pointer gap-3 rounded-xl border border-line bg-base p-4 transition-colors has-[:checked]:border-volt has-[:checked]:bg-volt/5"
          >
            <input type="radio" name="visibility" value={o.value} defaultChecked={current === o.value} className="mt-1 accent-[#c8ff3d]" />
            <span>
              <span className="block font-semibold">{o.title}</span>
              <span className="mt-0.5 block text-sm text-muted">{o.body}</span>
            </span>
          </label>
        ))}
      </fieldset>
      <div className="sm:w-48">
        <SubmitButton pendingLabel="Saving…" variant="secondary">
          Save
        </SubmitButton>
      </div>
    </form>
  );
}
