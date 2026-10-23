"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { SIDEBAR_ITEMS, type NavItem } from "@/config/sidebar-links";
import { useAuth } from "@/contexts/AuthContext";
import { cn } from "@/lib/utils";

const links = SIDEBAR_ITEMS.flatMap((group) => group.items);

export function HouseNav() {
    const pathname = usePathname();
    const { signOut } = useAuth();

    return (
        <>
            <div className="flex justify-end px-5 pt-3 md:hidden">
                <SignOutButton onClick={() => void signOut()} />
            </div>

            <nav
                aria-label="Navegação da casa"
                className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
            >
                <ul className="grid grid-cols-3">
                    {links.map((item) => (
                        <li key={item.url}>
                            <NavLink
                                item={item}
                                pathname={pathname}
                                className="h-16 w-full justify-center text-base"
                            />
                        </li>
                    ))}
                </ul>
            </nav>

            <nav
                aria-label="Navegação da casa"
                className="fixed inset-y-0 left-0 z-30 hidden w-40 flex-col border-r border-border px-6 py-12 md:flex"
            >
                <ul className="flex flex-col gap-1">
                    {links.map((item) => (
                        <li key={item.url}>
                            <NavLink item={item} pathname={pathname} className="text-lg" />
                        </li>
                    ))}
                </ul>
                <SignOutButton onClick={() => void signOut()} className="mt-auto" />
            </nav>
        </>
    );
}

function NavLink({
    item,
    pathname,
    className,
}: {
    item: NavItem;
    pathname: string;
    className?: string;
}) {
    const active = pathname === item.url;

    return (
        <Link
            href={item.url}
            aria-current={active ? "page" : undefined}
            className={cn(
                "inline-flex min-h-11 items-center rounded-md px-2 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                active ? "font-bold text-foreground" : "text-muted-foreground",
                className
            )}
        >
            {item.title}
        </Link>
    );
}

function SignOutButton({
    onClick,
    className,
}: {
    onClick: () => void;
    className?: string;
}) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={cn(
                "inline-flex min-h-11 items-center rounded-md px-2 text-sm text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
                className
            )}
        >
            Sair
        </button>
    );
}
