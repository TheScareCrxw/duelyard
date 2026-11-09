import { unstable_rethrow } from "next/navigation";
import type { ProblemDetail } from "@/types/api";

/**
 * Server-side client for the Spring Boot API.
 *
 * API_BASE_URL is intentionally not prefixed with NEXT_PUBLIC_: it is only read on the server,
 * so it never ships to the browser. Authenticated calls pass the user's access token (read from
 * an HttpOnly cookie by the caller) as a bearer token.
 */
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";
const REQUEST_TIMEOUT_MS = 5000;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface ApiRequestOptions {
  accessToken?: string;
}

export async function apiGet<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.accessToken) headers.Authorization = `Bearer ${options.accessToken}`;

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      headers,
      cache: "no-store",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (cause) {
    // Let Next.js control-flow errors (dynamic rendering bailouts etc.) propagate untouched.
    unstable_rethrow(cause);
    throw new ApiError(503, `API unreachable: ${(cause as Error).message}`);
  }

  if (!response.ok) {
    let detail: string | undefined;
    try {
      detail = ((await response.json()) as ProblemDetail).detail;
    } catch {
      // Body was not a problem document; fall back to the status text.
    }
    throw new ApiError(response.status, detail ?? response.statusText);
  }

  return (await response.json()) as T;
}
