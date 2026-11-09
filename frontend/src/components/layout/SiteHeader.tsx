import Link from "next/link";
import { Suspense } from "react";
import { Logo } from "@/components/ui/Logo";
import { SessionControls } from "./SessionControls";

const NAV = [
  { href: "/#games", label: "Games" },
  { href: "/#rankings", label: "Rankings" },
];

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-40 border-b border-line/70 bg-base/80 backdrop-blur-md">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex items-center gap-8">
          <Logo />
          <nav aria-label="Primary" className="hidden items-center gap-1 md:flex">
            {NAV.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="rounded-md px-3 py-2 text-sm font-medium text-muted transition-colors hover:text-fg"
              >
                {item.label}
              </Link>
            ))}
          </nav>
        </div>
        <Suspense fallback={<div className="h-9 w-40" aria-hidden />}>
          <SessionControls />
        </Suspense>
      </div>
    </header>
  );
}
