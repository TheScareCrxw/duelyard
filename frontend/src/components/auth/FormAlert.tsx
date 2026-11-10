export function FormAlert({ tone, children }: { tone: "error" | "success" | "info"; children: React.ReactNode }) {
  const styles = {
    error: "border-loss/40 bg-loss/10 text-loss",
    success: "border-win/40 bg-win/10 text-win",
    info: "border-line bg-raised text-muted",
  }[tone];
  return (
    <p role={tone === "error" ? "alert" : "status"} className={`rounded-lg border px-3.5 py-2.5 text-sm ${styles}`}>
      {children}
    </p>
  );
}
