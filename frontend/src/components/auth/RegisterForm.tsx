"use client";

import { useActionState } from "react";
import { registerAction } from "@/app/(auth)/actions";
import { initialAuthFormState } from "@/types/auth";
import { FormAlert } from "./FormAlert";
import { FormField } from "./FormField";
import { SubmitButton } from "./SubmitButton";

export function RegisterForm() {
  const [state, action] = useActionState(registerAction, initialAuthFormState);
  const errors = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
      <FormField
        name="username"
        label="Username"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        minLength={3}
        maxLength={20}
        pattern="[A-Za-z0-9_]{3,20}"
        required
        defaultValue={state.values?.username}
        error={errors.username}
        hint="3–20 characters: letters, numbers and underscores. Shown on leaderboards."
      />
      <FormField
        name="email"
        label="Email"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values?.email}
        error={errors.email}
      />
      <FormField
        name="password"
        label="Password"
        type="password"
        autoComplete="new-password"
        minLength={8}
        required
        error={errors.password}
        hint="At least 8 characters with uppercase, lowercase, a number and a symbol."
      />
      <FormField
        name="confirmPassword"
        label="Confirm password"
        type="password"
        autoComplete="new-password"
        required
        error={errors.confirmPassword}
      />
      <SubmitButton pendingLabel="Creating account…">Create account</SubmitButton>
    </form>
  );
}
