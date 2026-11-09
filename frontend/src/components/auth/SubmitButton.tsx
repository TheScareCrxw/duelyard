"use client";

import { useFormStatus } from "react-dom";
import { buttonClasses } from "@/components/ui/Button";

export function SubmitButton({
  children,
  pendingLabel,
  variant = "primary",
}: {
  children: React.ReactNode;
  pendingLabel: string;
  variant?: "primary" | "secondary" | "ghost";
}) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} aria-busy={pending} className={buttonClasses(variant, "md", "w-full")}>
      {pending ? pendingLabel : children}
    </button>
  );
}
