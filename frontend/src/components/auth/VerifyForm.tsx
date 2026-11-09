"use client";

import { useActionState } from "react";
import { resendCodeAction, verifyAction } from "@/app/(auth)/actions";
import { initialAuthFormState } from "@/types/auth";
import { FormAlert } from "./FormAlert";
import { FormField } from "./FormField";
import { SubmitButton } from "./SubmitButton";

export function VerifyForm({ username }: { username?: string }) {
  const [state, action] = useActionState(verifyAction, initialAuthFormState);
  const [resendState, resend] = useActionState(resendCodeAction, initialAuthFormState);
  const errors = state.fieldErrors ?? {};
  const currentUsername = state.values?.username ?? resendState.values?.username ?? username;

  return (
    <div className="space-y-6">
      <form action={action} className="space-y-4" noValidate>
        {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
        <FormField
          name="username"
          label="Username"
          autoComplete="username"
          autoCapitalize="none"
          required
          defaultValue={currentUsername}
          readOnly={Boolean(username)}
          error={errors.username}
        />
        <FormField
          name="code"
          label="Verification code"
          inputMode="numeric"
          autoComplete="one-time-code"
          pattern="[0-9]{6}"
          maxLength={6}
          placeholder="123456"
          required
          error={errors.code}
        />
        <SubmitButton pendingLabel="Verifying…">Verify email</SubmitButton>
      </form>

      <form action={resend} className="space-y-3 border-t border-line pt-5">
        <input type="hidden" name="username" value={currentUsername ?? ""} />
        {resendState.message && <FormAlert tone="success">{resendState.message}</FormAlert>}
        {resendState.error && <FormAlert tone="error">{resendState.error}</FormAlert>}
        {resendState.fieldErrors?.username && <FormAlert tone="error">{resendState.fieldErrors.username}</FormAlert>}
        <p className="text-sm text-muted">Didn&apos;t get it? Check your spam folder, or</p>
        <SubmitButton pendingLabel="Sending…" variant="secondary">
          Send a new code
        </SubmitButton>
      </form>
    </div>
  );
}
