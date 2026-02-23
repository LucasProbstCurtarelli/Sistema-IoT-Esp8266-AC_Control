/**
 * API configuration constants.
 * 
 * These values can be overridden via environment variables.
 */
export const API_CONFIG = {
  /**
   * Request timeout in milliseconds.
   * Default: 10000 (10 seconds)
   * Can be overridden via NEXT_PUBLIC_API_TIMEOUT environment variable.
   */
  timeout: parseInt(process.env.NEXT_PUBLIC_API_TIMEOUT || '10000', 10),
  
  /**
   * API base URL.
   * Default: http://localhost:8080
   * Can be overridden via NEXT_PUBLIC_API_URL environment variable.
   */
  baseURL: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080',
} as const;
