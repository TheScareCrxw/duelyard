"use client";

import Link from "next/link";
import { useActionState } from "react";
import { loginAction } from "@/app/(auth)/actions";
import { initialAuthFormState } from "@/types/auth";
import { FormAlert } from "./FormAlert";
import { FormField } from "./FormField";
import { SubmitButton } from "./SubmitButton";

export function LoginForm({ next, identifier }: { next: string; identifier?: string }) {
  const [state, action] = useActionState(loginAction, initialAuthFormState);
  const errors = state.fieldErrors ?? {};

  return (
    <form action={action} className="space-y-4" noValidate>
      {state.error && <FormAlert tone="error">{state.error}</FormAlert>}
      <input type="hidden" name="next" value={next} />
      <FormField
        name="identifier"
        label="Username or email"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        required
        defaultValue={state.values?.identifier ?? identifier}
        error={errors.identifier}
      />
      <div>
        <FormField
          name="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          error={errors.password}
        />
        <div className="mt-2 text-right">
          <Link href="/forgot-password" className="text-xs font-medium text-muted hover:text-fg">
            Forgot password?
          </Link>
        </div>
      </div>
      <SubmitButton pendingLabel="Signing in…">Sign in</SubmitButton>
    </form>
  );
}
