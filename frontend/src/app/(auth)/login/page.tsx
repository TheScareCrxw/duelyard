import type { Metadata } from "next";
import { PlaceholderPanel } from "@/components/layout/PlaceholderPanel";

export const metadata: Metadata = { title: "Sign in" };

export default function LoginPage() {
  return <PlaceholderPanel title="Sign in" body="Accounts are almost here. Email and Google sign-in are being wired up next." />;
}
