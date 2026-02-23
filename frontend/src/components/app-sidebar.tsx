"use client";

import * as React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
    ChevronRight,
    LogOut,
} from "lucide-react";
import { LogoBrand } from "@/components/logo-brand";

import {
    Sidebar,
    SidebarContent,
    SidebarFooter,
    SidebarGroup,
    SidebarGroupContent,
    SidebarGroupLabel,
    SidebarHeader,
    SidebarMenu,
    SidebarMenuButton,
    SidebarMenuItem,
    SidebarMenuSub,
    SidebarMenuSubButton,
    SidebarMenuSubItem,
    SidebarRail,
    SidebarSeparator,
} from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

import { useAuth } from "@/contexts/AuthContext";
import { SIDEBAR_ITEMS } from "@/config/sidebar-links";

function CollapsibleItem({
    item,
    pathname,
    shouldBeOpen,
}: {
    item: (typeof SIDEBAR_ITEMS)[number]["items"][number];
    pathname: string;
    shouldBeOpen: boolean;
}) {
    const [open, setOpen] = React.useState(false);

    React.useEffect(() => {
        setOpen(shouldBeOpen);
    }, [shouldBeOpen]);

    return (
        <Collapsible
            asChild
            open={open}
            onOpenChange={setOpen}
            className="group/collapsible"
        >
            <SidebarMenuItem>
                <CollapsibleTrigger asChild>
                    <SidebarMenuButton
                        tooltip={item.title}
                        isActive={pathname === item.url}
                    >
                        {item.icon && <item.icon />}
                        <span>{item.title}</span>
                        <ChevronRight className="ml-auto transition-transform duration-200 group-data-[state=open]/collapsible:rotate-90 group-data-[collapsible=icon]:hidden" />
                    </SidebarMenuButton>
                </CollapsibleTrigger>
                <CollapsibleContent>
                    <SidebarMenuSub>
                        {item.items?.map((subItem) => (
                            <SidebarMenuSubItem key={subItem.title}>
                                <SidebarMenuSubButton
                                    asChild
                                    isActive={pathname === subItem.url}
                                >
                                    <Link href={subItem.url}>
                                        <span>{subItem.title}</span>
                                    </Link>
                                </SidebarMenuSubButton>
                            </SidebarMenuSubItem>
                        ))}
                    </SidebarMenuSub>
                </CollapsibleContent>
            </SidebarMenuItem>
        </Collapsible>
    );
}

function getInitials(name?: string): string {
    if (!name) return "AD";
    return name
        .split(" ")
        .map((n) => n[0])
        .slice(0, 2)
        .join("")
        .toUpperCase();
}

export function AppSidebar({ ...props }: React.ComponentProps<typeof Sidebar>) {
    const { user, signOut } = useAuth();
    const pathname = usePathname();

    const handleLogout = async () => {
        await signOut();
    };

    return (
        <Sidebar collapsible="icon" {...props}>
            <SidebarHeader>
                <SidebarMenu>
                    <SidebarMenuItem>
                        <SidebarMenuButton size="lg" asChild>
                            <Link href="/dashboard" className="group/logo-link">
                                <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-transparent transition-all duration-300 ease-in-out group-hover/logo-link:scale-105 shrink-0">
                                    <LogoBrand className="size-6 shrink-0" animationType="slide" />
                                </div>
                                <div className="grid flex-1 text-left text-sm leading-tight group-data-[collapsible=icon]:hidden">
                                    <span className="truncate font-semibold">
                                        Automação
                                    </span>
                                    <span className="truncate text-xs">
                                        Residencial
                                    </span>
                                </div>
                            </Link>
                        </SidebarMenuButton>
                    </SidebarMenuItem>
                </SidebarMenu>
            </SidebarHeader>

            <SidebarSeparator />

            <SidebarContent className="overflow-hidden">
                {SIDEBAR_ITEMS.map((group, index) => (
                    <React.Fragment key={`${group.title}-${index}`}>
                        {index > 0 && <SidebarSeparator />}
                        <SidebarGroup>
                            {group.title && <SidebarGroupLabel>{group.title}</SidebarGroupLabel>}
                            <SidebarGroupContent>
                                <SidebarMenu>
                                    {group.items.map((item) => {
                                        const hasSubItems =
                                            item.items && item.items.length > 0;

                                        const isSubItemActive = item.items?.some(
                                            (sub) => sub.url === pathname
                                        ) ?? false;
                                        const shouldBeOpen = (item.isActive ?? false) || isSubItemActive;

                                        return hasSubItems ? (
                                            <CollapsibleItem
                                                key={item.title}
                                                item={item}
                                                pathname={pathname}
                                                shouldBeOpen={shouldBeOpen}
                                            />
                                        ) : (
                                            <SidebarMenuItem key={item.title}>
                                                <SidebarMenuButton
                                                    asChild
                                                    tooltip={item.title}
                                                    isActive={pathname === item.url}
                                                >
                                                    <Link href={item.url}>
                                                        {item.icon && <item.icon />}
                                                        <span>{item.title}</span>
                                                    </Link>
                                                </SidebarMenuButton>
                                            </SidebarMenuItem>
                                        );
                                    })}
                                </SidebarMenu>
                            </SidebarGroupContent>
                        </SidebarGroup>
                    </React.Fragment>
                ))}
            </SidebarContent>

            <SidebarFooter>
                <SidebarMenu>
                    <SidebarMenuItem>
                        <div className="flex items-center gap-3 w-full px-2 py-2 group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:gap-0 group-data-[collapsible=icon]:p-2">
                            <Avatar className="h-8 w-8 rounded-lg shrink-0">
                                <AvatarImage
                                    src={user?.avatar || ""}
                                    alt={user?.name || "Usuário"}
                                />
                                <AvatarFallback className="rounded-lg bg-muted text-muted-foreground">
                                    {getInitials(user?.name)}
                                </AvatarFallback>
                            </Avatar>
                            <div className="grid flex-1 text-left text-sm leading-tight min-w-0 group-data-[collapsible=icon]:hidden">
                                <span className="truncate font-semibold text-foreground">
                                    {user?.name || "Usuário"}
                                </span>
                                <span className="truncate text-xs text-muted-foreground">
                                    {user?.email || "admin@automacao.com"}
                                </span>
                            </div>
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={handleLogout}
                                className="shrink-0 group-data-[collapsible=icon]:hidden"
                                title="Sair"
                            >
                                <LogOut className="h-4 w-4" />
                            </Button>
                        </div>
                    </SidebarMenuItem>
                </SidebarMenu>
            </SidebarFooter>
            <SidebarRail />
        </Sidebar>
    );
}
