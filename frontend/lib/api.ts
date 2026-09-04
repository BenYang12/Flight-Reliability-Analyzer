// Only place app talks to network
// Two call paths
// 1. Server Components -> Spring Boot directly (funcs marked "server)
// 2. Client components -> our own /api/* proxies

import type {
  FlightAnalysisResponse,
  FlightOperationLive,
  OptimalWindowResponse,
  SearchResponse,
} from "@/lib/types";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

const REVALIDATE_HISTORY_SECONDS = 86400;
const REVALIDATE_LIVE_SECONDS = 3600;

export interface ApiError {
  /** The HTTP status, or 0 when the request never reached the server. */
  status: number;
  message: string;
}

export type ApiResult<T> =
  | { ok: true; data: T }
  | { ok: false; error: ApiError };

async function readError(response: Response): Promise<ApiError> {
  try {
    const body = await response.json();
    // Guard the shape anyway: types are a claim about the server, not a promise.
    if (body && typeof body.message === "string") {
      return { status: response.status, message: body.message };
    }
  } catch {}
  return {
    status: response.status,
    message: response.statusText || "Request failed",
  };
}

async function request<T>(
  url: string,
  init?: RequestInit,
): Promise<ApiResult<T>> {
  try {
    const response = await fetch(url, init);
    if (!response.ok) {
      return { ok: false, error: await readError(response) };
    }
    return { ok: true, data: (await response.json()) as T };
  } catch (cause) {
    return {
      ok: false,
      error: {
        status: 0,
        message:
          cause instanceof Error ? cause.message : "Network request failed",
      },
    };
  }
}

// server: called from Server Components, straight to Spring Boot
export function fetchFlight(
  flightNumber: string,
): Promise<ApiResult<FlightAnalysisResponse>> {
  return request(
    `${API_BASE_URL}/api/flights/${encodeURIComponent(flightNumber)}`,
    { next: { revalidate: REVALIDATE_HISTORY_SECONDS } },
  );
}

export function fetchOptimalWindow(
  origin: string,
  dest: string,
): Promise<ApiResult<OptimalWindowResponse>> {
  const query = new URLSearchParams({ origin, dest });
  return request(`${API_BASE_URL}/api/optimal-window?${query}`, {
    next: { revalidate: REVALIDATE_HISTORY_SECONDS },
  });
}

export function fetchLiveOperations(
  flightNumber: string,
  page = 0,
  size = 20,
): Promise<ApiResult<FlightOperationLive[]>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request(
    `${API_BASE_URL}/api/flights/${encodeURIComponent(flightNumber)}/operations?${query}`,
    { next: { revalidate: REVALIDATE_LIVE_SECONDS } },
  );
}

// browser: called from "use client" components, through our own proxies
export function searchQuery(q: string): Promise<ApiResult<SearchResponse>> {
  const query = new URLSearchParams({ q });
  // A relative URL: same origin, so the browser never learns the backend's address.
  return request(`/api/search?${query}`);
}

export function isNotFound(error: ApiError): boolean {
  return error.status === 404;
}
