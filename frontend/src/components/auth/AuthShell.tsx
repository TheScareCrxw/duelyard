import type { ReactNode } from "react";

/** Centered card layout shared by all auth pages. */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle?: ReactNode;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="relative overflow-hidden">
      <div aria-hidden className="bg-grid absolute inset-0 opacity-60" />
      <div className="relative mx-auto flex max-w-md flex-col px-4 py-14 sm:py-20">
        <div className="rounded-2xl border border-line bg-surface/95 p-6 shadow-2xl shadow-black/40 backdrop-blur sm:p-8">
          <h1 className="font-display text-3xl font-bold tracking-tight uppercase">{title}</h1>
          {subtitle && <p className="mt-2 text-sm text-muted">{subtitle}</p>}
          <div className="mt-7">{children}</div>
        </div>
        {footer && <div className="mt-6 text-center text-sm text-muted">{footer}</div>}
      </div>
    </div>
  );
}
