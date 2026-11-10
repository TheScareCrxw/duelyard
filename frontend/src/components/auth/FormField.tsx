import type { InputHTMLAttributes } from "react";

interface FormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  name: string;
  label: string;
  error?: string;
  hint?: string;
}

export function FormField({ name, label, error, hint, id = name, ...input }: FormFieldProps) {
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined;
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-medium text-fg">
        {label}
      </label>
      <input
        id={id}
        name={name}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`mt-1.5 block h-11 w-full rounded-lg border bg-base px-3.5 text-sm text-fg placeholder:text-faint transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-volt/60 ${
          error ? "border-loss" : "border-line hover:border-faint focus:border-volt"
        }`}
        {...input}
      />
      {error ? (
        <p id={`${id}-error`} className="mt-1.5 text-xs text-loss">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-faint">
          {hint}
        </p>
      ) : null}
    </div>
  );
}
