"use client";

import { useActionState } from "react";
import { updateUsernameAction } from "@/app/settings/actions";
import { FormAlert } from "@/components/auth/FormAlert";
import { FormField } from "@/components/auth/FormField";
import { SubmitButton } from "@/components/auth/SubmitButton";
import { initialAuthFormState } from "@/types/auth";

export function UsernameSettingsForm({ current, availableAt }: { current: string; availableAt: string | null }) {
  const [state, action] = useActionState(updateUsernameAction, initialAuthFormState);
  const locked = availableAt !== null && !state.message;
  const unlockDate = availableAt
    ? new Date(availableAt).toLocaleDateString("en-US", { month: "long", day: "numeric", year: "numeric" })
    : null;

  return (
    <form action={action} className="space-y-4" noValidate>
      {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
      {state.message && <FormAlert tone="success">{state.message}</FormAlert>}
      <FormField
        name="username"
        label="Username"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        maxLength={20}
        defaultValue={state.values?.username ?? current}
        disabled={locked}
        error={state.fieldErrors?.username}
        hint={
          locked
            ? `You can change it again on ${unlockDate}.`
            : "You sign in with this name. You can change it once every 30 days."
        }
      />
      {!locked && (
        <div className="sm:w-48">
          <SubmitButton pendingLabel="Saving…" variant="secondary">
            Change username
          </SubmitButton>
        </div>
      )}
    </form>
  );
}
