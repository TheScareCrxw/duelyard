import Link from "next/link";
import { logoutAction } from "@/app/(auth)/actions";
import { PlayerAvatar } from "@/components/player/PlayerAvatar";
import { buttonClasses, LinkButton } from "@/components/ui/Button";
import { getSessionUser } from "@/lib/auth/dal";

/** Header controls that depend on the session. Rendered inside Suspense by SiteHeader. */
export async function SessionControls() {
  const session = await getSessionUser();

  if (session.status === "signed-out") {
    return <GuestControls />;
  }

  return (
    <div className="flex items-center gap-2">
      {session.status === "signed-in" && (
        <Link
          href="/dashboard"
          className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-sm font-semibold transition-colors hover:bg-raised"
        >
          <PlayerAvatar username={session.user.username} avatarUrl={session.user.avatarUrl} size="sm" />
          <span className="hidden max-w-[10rem] truncate sm:inline">{session.user.username}</span>
        </Link>
      )}
      <form action={logoutAction}>
        <button type="submit" className={buttonClasses("ghost", "sm")}>
          Sign out
        </button>
      </form>
    </div>
  );
}

export function GuestControls() {
  return (
    <div className="flex items-center gap-2">
      <LinkButton href="/login" variant="ghost" size="sm">
        Sign in
      </LinkButton>
      <LinkButton href="/register" size="sm">
        Register
      </LinkButton>
    </div>
  );
}
