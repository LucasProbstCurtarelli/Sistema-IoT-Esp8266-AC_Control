"use client";

import { ErrorBoundary } from "./ErrorBoundary";
import type { ReactNode } from "react";

interface ErrorBoundaryWrapperProps {
  children: ReactNode;
}

/**
 * Client component wrapper for ErrorBoundary to use in server components.
 */
export function ErrorBoundaryWrapper({ children }: ErrorBoundaryWrapperProps) {
  return (
    <ErrorBoundary
      onError={(error, errorInfo) => {
        // In production, send to error reporting service
        if (process.env.NODE_ENV === "production") {
          // Example: Sentry.captureException(error, { contexts: { react: errorInfo } });
          console.error("Error caught by ErrorBoundary:", error, errorInfo);
        }
      }}
    >
      {children}
    </ErrorBoundary>
  );
}
