import { redirect } from "next/navigation";
import { requireUser } from "@/lib/auth/dal";

/** /profile → your own profile. */
export default async function OwnProfilePage() {
  const user = await requireUser("/profile");
  redirect(`/profile/${encodeURIComponent(user.username)}`);
}
