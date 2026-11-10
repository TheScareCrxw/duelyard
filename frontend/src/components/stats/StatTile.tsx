export function StatTile({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="rounded-xl border border-line bg-surface px-4 py-3.5">
      <p className="text-xs font-semibold tracking-wider text-muted uppercase">{label}</p>
      <p className="mt-1 font-display text-2xl font-bold tabular-nums">{value}</p>
      {hint && <p className="mt-0.5 text-xs text-faint">{hint}</p>}
    </div>
  );
}

export function formatWinRate(winRate: number | null): string {
  return winRate === null ? "–" : `${Math.round(winRate * 100)}%`;
}
