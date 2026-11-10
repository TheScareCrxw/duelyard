import Link from "next/link";

export function Logo() {
  return (
    <Link href="/" className="group inline-flex items-center gap-2.5" aria-label="Duelyard home">
      <svg viewBox="0 0 32 32" className="h-8 w-8" aria-hidden>
        <rect width="32" height="32" rx="8" className="fill-raised" />
        <path d="M9 8h7a8 8 0 0 1 0 16H9z" className="fill-volt" />
        <path d="M23 8v16" stroke="currentColor" strokeWidth="3" strokeLinecap="round" className="text-fg" />
      </svg>
      <span className="font-display text-xl font-bold tracking-wide text-fg uppercase">Duelyard</span>
    </Link>
  );
}
