import type { ReactNode } from "react";
import { RealtimeProvider } from "@/contexts/RealtimeContext";

/** Play and match pages share one game-server connection across navigations. */
export default function GameLayout({ children }: { children: ReactNode }) {
  return <RealtimeProvider>{children}</RealtimeProvider>;
}
