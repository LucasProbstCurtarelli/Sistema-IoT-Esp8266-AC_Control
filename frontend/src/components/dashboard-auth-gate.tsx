"use client";

import * as React from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/contexts/AuthContext";

/**
 * Mirrors Unifique DashboardShell: blocks dashboard chrome until auth is resolved,
 * then redirects unauthenticated users to login.
 */
export function DashboardAuthGate({ children }: { children: React.ReactNode }) {
    const { isAuthenticated, isLoading } = useAuth();
    const router = useRouter();

    React.useEffect(() => {
        if (!isLoading && !isAuthenticated) {
            router.replace("/login");
        }
    }, [isLoading, isAuthenticated, router]);

    if (isLoading) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-background text-sm text-muted-foreground">
                Carregando…
            </div>
        );
    }

    if (!isAuthenticated) {
        return (
            <div className="flex min-h-screen items-center justify-center bg-background text-sm text-muted-foreground">
                Redirecionando…
            </div>
        );
    }

    return <>{children}</>;
}
