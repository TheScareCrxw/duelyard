import type { Metadata } from "next";
import { PlaceholderPanel } from "@/components/layout/PlaceholderPanel";

export const metadata: Metadata = { title: "Register" };

export default function RegisterPage() {
  return <PlaceholderPanel title="Create account" body="Registration opens soon. Email and Google sign-up are being wired up next." />;
}
