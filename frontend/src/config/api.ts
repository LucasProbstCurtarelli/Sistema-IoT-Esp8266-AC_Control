function normalizeApiBase(raw: string | undefined): string {
    if (raw === undefined || raw.trim() === "") {
        return "";
    }
    return raw.replace(/\/+$/, "");
}

/**
 * Empty base URL = same-origin; Next.js rewrites `/api/*` to Spring (see `next.config.ts`).
 * Set `NEXT_PUBLIC_API_URL` only when the browser must call the API on another host.
 */
export const API_CONFIG = {
    timeout: parseInt(process.env.NEXT_PUBLIC_API_TIMEOUT || "10000", 10),
    baseURL: normalizeApiBase(process.env.NEXT_PUBLIC_API_URL),
} as const;
