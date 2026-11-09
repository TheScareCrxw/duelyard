import { LinkButton } from "@/components/ui/Button";

/** Centered panel for routes whose functionality lands in a later phase. */
export function PlaceholderPanel({ title, body }: { title: string; body: string }) {
  return (
    <div className="mx-auto flex max-w-md flex-col items-center px-4 py-24 text-center">
      <h1 className="font-display text-3xl font-bold tracking-tight uppercase">{title}</h1>
      <p className="mt-3 text-muted">{body}</p>
      <LinkButton href="/" variant="secondary" className="mt-8">
        Back to home
      </LinkButton>
    </div>
  );
}
