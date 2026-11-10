export function SiteFooter() {
  return (
    <footer className="border-t border-line/70">
      <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-8 text-sm text-faint sm:flex-row sm:items-center sm:justify-between sm:px-6">
        <p>© {new Date().getFullYear()} Duelyard</p>
        <p>Head-to-head mini-games. Every match counts.</p>
      </div>
    </footer>
  );
}
