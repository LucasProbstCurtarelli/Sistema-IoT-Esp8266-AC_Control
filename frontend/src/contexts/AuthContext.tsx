"use client";

import * as React from "react";
import { usePathname, useRouter } from "next/navigation";
import type { User } from "@/types/auth";
import { toast } from "sonner";

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    signOut: () => Promise<void>;
}

const AuthContext = React.createContext<AuthContextType>({} as AuthContextType);

function getCookie(name: string) {
    if (typeof document === "undefined") return null;
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) {
        const cookieValue = parts.pop()?.split(";").shift();
        if (cookieValue) {
            try {
                return decodeURIComponent(cookieValue);
            } catch {
                return cookieValue;
            }
        }
    }
    return null;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
    const [user, setUser] = React.useState<User | null>(null);
    const [isLoading, setIsLoading] = React.useState(true);
    const pathname = usePathname();
    const router = useRouter();

    const loadUserFromCookie = React.useCallback(() => {
        try {
            const userCookie = getCookie("automation.user");
            if (userCookie) {
                const parsedUser = JSON.parse(userCookie);
                setUser(parsedUser);
                return parsedUser;
            } else {
                setUser(null);
                return null;
            }
        } catch (error) {
            setUser(null);
            return null;
        }
    }, []);

    const signOut = React.useCallback(async () => {
        try {
            setUser(null);
            // Clear user cookie
            document.cookie = "automation.user=; path=/; max-age=0";
            // Note: authToken cookie is httpOnly, so it cannot be cleared from JavaScript
            // Backend should handle token invalidation if needed
            router.push("/login");
            toast.success("Logout realizado com sucesso");
        } catch (error) {
            toast.error("Erro ao fazer logout");
        }
    }, [router]);

    React.useEffect(() => {
        loadUserFromCookie();
        setIsLoading(false);
    }, [loadUserFromCookie]);

    React.useEffect(() => {
        if (!isLoading) {
            loadUserFromCookie();
        }
    }, [pathname, isLoading, loadUserFromCookie]);

    const value = React.useMemo(
        () => ({
            user,
            isAuthenticated: !!user,
            isLoading,
            signOut,
        }),
        [user, isLoading, signOut]
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
