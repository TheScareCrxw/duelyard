"use client";

import { useEffect } from "react";
import { buttonClasses } from "@/components/ui/Button";

/** Route-level error boundary (e.g. the API is unreachable while loading a signed-in page). */
export default function Error({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center">
      <h1 className="font-display text-3xl font-bold tracking-tight uppercase">Technical foul</h1>
      <p className="mt-3 text-muted">Something went wrong loading this page. Please try again in a moment.</p>
      <button type="button" onClick={reset} className={buttonClasses("secondary", "md", "mt-8")}>
        Try again
      </button>
    </div>
  );
}
