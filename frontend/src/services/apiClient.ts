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
    /** Stable machine-readable code from the API (e.g. "USERNAME_TAKEN"), if provided. */
    readonly code?: string,
    /** Extra problem properties (e.g. availableAt). */
    readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface ApiRequestOptions {
  accessToken?: string;
}

async function request<T>(method: string, path: string, body: unknown, options: ApiRequestOptions): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.accessToken) headers.Authorization = `Bearer ${options.accessToken}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (cause) {
    // Let Next.js control-flow errors (dynamic rendering bailouts etc.) propagate untouched.
    unstable_rethrow(cause);
    throw new ApiError(503, `API unreachable: ${(cause as Error).message}`);
  }

  if (!response.ok) {
    let problem: (ProblemDetail & Record<string, unknown>) | undefined;
    try {
      problem = (await response.json()) as ProblemDetail & Record<string, unknown>;
    } catch {
      // Body was not a problem document; fall back to the status text.
    }
    const { detail, code, ...rest } = problem ?? {};
    throw new ApiError(
      response.status,
      typeof detail === "string" ? detail : response.statusText,
      typeof code === "string" ? code : undefined,
      rest,
    );
  }

  return (await response.json()) as T;
}

export function apiGet<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  return request<T>("GET", path, undefined, options);
}

export function apiPatch<T>(path: string, body: unknown, options: ApiRequestOptions = {}): Promise<T> {
  return request<T>("PATCH", path, body, options);
}
