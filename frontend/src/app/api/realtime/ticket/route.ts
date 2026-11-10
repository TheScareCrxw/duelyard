import { NextResponse, type NextRequest } from "next/server";
import { getAccessToken } from "@/lib/auth/dal";
import { ApiError, apiPost } from "@/services/apiClient";

/**
 * Exchanges the HttpOnly session cookie for a one-time WebSocket ticket. Same-origin only: the
 * Origin check plus SameSite=Lax cookies mean another site can't obtain a ticket for the user.
 */
export async function POST(request: NextRequest) {
  const origin = request.headers.get("origin");
  if (origin && origin !== request.nextUrl.origin) {
    return NextResponse.json({ error: "forbidden" }, { status: 403 });
  }
  const accessToken = await getAccessToken();
  if (!accessToken) return NextResponse.json({ error: "unauthenticated" }, { status: 401 });

  try {
    const { ticket } = await apiPost<{ ticket: string }>("/api/realtime/tickets", {}, { accessToken });
    return NextResponse.json({ ticket }, { headers: { "Cache-Control": "no-store" } });
  } catch (error) {
    const status = error instanceof ApiError ? (error.status === 401 ? 401 : 502) : 502;
    return NextResponse.json({ error: "ticket_failed" }, { status });
  }
}
