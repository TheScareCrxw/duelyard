"use client";

import { useActionState } from "react";
import { forgotPasswordAction, resetPasswordAction } from "@/app/(auth)/actions";
import { initialAuthFormState } from "@/types/auth";
import { FormAlert } from "./FormAlert";
import { FormField } from "./FormField";
import { SubmitButton } from "./SubmitButton";

/** Two steps: request a reset code, then set a new password with it. */
export function ForgotPasswordForm() {
  const [requestState, request] = useActionState(forgotPasswordAction, initialAuthFormState);
  const [resetState, reset] = useActionState(resetPasswordAction, initialAuthFormState);

  const onResetStep = requestState.step === "reset" || resetState.step === "reset";
  const identifier = resetState.values?.identifier ?? requestState.values?.identifier ?? "";

  if (!onResetStep) {
    const errors = requestState.fieldErrors ?? {};
    return (
      <form action={request} className="space-y-4" noValidate>
        {requestState.error && <FormAlert tone="error">{requestState.error}</FormAlert>}
        <FormField
          name="identifier"
          label="Username or email"
          autoComplete="username"
          autoCapitalize="none"
          required
          defaultValue={identifier}
          error={errors.identifier}
        />
        <SubmitButton pendingLabel="Sending…">Send reset code</SubmitButton>
      </form>
    );
  }

  const errors = resetState.fieldErrors ?? {};
  return (
    <form action={reset} className="space-y-4" noValidate>
      {requestState.message && !resetState.error && <FormAlert tone="info">{requestState.message}</FormAlert>}
      {resetState.error && <FormAlert tone="error">{resetState.error}</FormAlert>}
      <input type="hidden" name="identifier" value={identifier} />
      <FormField
        name="code"
        label="Reset code"
        inputMode="numeric"
        autoComplete="one-time-code"
        pattern="[0-9]{6}"
        maxLength={6}
        placeholder="123456"
        required
        error={errors.code}
      />
      <FormField
        name="password"
        label="New password"
        type="password"
        autoComplete="new-password"
        required
        error={errors.password}
        hint="At least 8 characters with uppercase, lowercase, a number and a symbol."
      />
      <SubmitButton pendingLabel="Saving…">Set new password</SubmitButton>
    </form>
  );
}
