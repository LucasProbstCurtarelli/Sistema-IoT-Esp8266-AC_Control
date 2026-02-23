"use client";

import * as React from "react";
import { usePathname, useRouter } from "next/navigation";
import type { User } from "@/types/auth";
import { toast } from "sonner";
import { api } from "@/services/api";

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    signOut: () => Promise<void>;
    refreshUser: () => Promise<void>;
}

const AuthContext = React.createContext<AuthContextType>({} as AuthContextType);

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = React.useState<User | null>(null);
    const [isLoading, setIsLoading] = React.useState(true);
    const pathname = usePathname();
    const router = useRouter();

    const fetchUserFromBackend = React.useCallback(async () => {
        try {
            const response = await api.get("/api/me");
            if (response.status === 200 && response.data) {
                const userData: User = {
                    id: response.data.id,
                    name: response.data.name,
                    email: response.data.email,
                };
                setUser(userData);
                return userData;
            } else {
                setUser(null);
                return null;
            }
        } catch (error: any) {
            // User is not authenticated or token is invalid
            // This is expected when user is not logged in, so we don't log it as an error
            const status = error?.response?.status;
            if (status === 401 || status === 403) {
                // Expected: user is not authenticated
                if (process.env.NODE_ENV === 'development') {
                    console.log('[AuthContext] User not authenticated (expected if not logged in)');
                }
            } else {
                // Unexpected error (network, server error, etc.)
                console.error('[AuthContext] Error fetching user:', {
                    status,
                    message: error?.message,
                });
            }
            setUser(null);
            return null;
        }
    }, []);

    const refreshUser = React.useCallback(async () => {
        await fetchUserFromBackend();
    }, [fetchUserFromBackend]);

    const signOut = React.useCallback(async () => {
        try {
            // Call backend logout endpoint to revoke token
            try {
                await api.post("/api/logout");
            } catch (error) {
                // Log error but continue with logout
                console.error("Error calling logout endpoint:", error);
            }
            setUser(null);
            router.push("/login");
            toast.success("Logout realizado com sucesso");
        } catch (error) {
            toast.error("Erro ao fazer logout");
        }
    }, [router]);

    React.useEffect(() => {
        // Fetch user from backend on mount
        fetchUserFromBackend().finally(() => {
            setIsLoading(false);
        });
    }, [fetchUserFromBackend]);

    React.useEffect(() => {
        // Refresh user data when pathname changes (e.g., after login)
        if (!isLoading && pathname !== "/login") {
            fetchUserFromBackend();
        }
    }, [pathname, isLoading, fetchUserFromBackend]);

    const value = React.useMemo(
        () => ({
            user,
            isAuthenticated: !!user,
            isLoading,
            signOut,
            refreshUser,
        }),
        [user, isLoading, signOut, refreshUser]
    );

    return (
        <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
    );
}

export function useAuth() {
    const context = React.useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth deve ser usado dentro de um AuthProvider");
    }
    return context;
}
