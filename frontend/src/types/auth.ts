/** State returned by auth server actions to their forms (via useActionState). */
export interface AuthFormState {
  /** Form-level error message. */
  error?: string;
  /** Per-field error messages, keyed by input name. */
  fieldErrors?: Partial<Record<string, string>>;
  /** Non-error status message (e.g. "Code sent"). */
  message?: string;
  /** Echoed non-secret values, so fields keep their content after a failed submit. */
  values?: Partial<Record<string, string>>;
  /** Multi-step forms: which step to show next. */
  step?: "request" | "reset";
}

export const initialAuthFormState: AuthFormState = {};
