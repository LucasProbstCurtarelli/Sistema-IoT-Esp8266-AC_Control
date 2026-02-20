import { cookies } from "next/headers";
import { AppSidebar } from "@/components/app-sidebar";
import {
    SidebarInset,
    SidebarProvider,
    SidebarTrigger,
} from "@/components/ui/sidebar";
import { Separator } from "@/components/ui/separator";

export default async function DashboardLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    const cookieStore = await cookies();
    const sidebarState = cookieStore.get("sidebar_state")?.value;
    const defaultOpen = sidebarState ? sidebarState === "true" : true;
    return (
        <SidebarProvider defaultOpen={defaultOpen}>
            <AppSidebar />
            <SidebarInset className="flex flex-col overflow-hidden">
                <header className="fixed top-0 z-50 flex h-16 shrink-0 items-center gap-2 border-b bg-background shadow-md px-4 transition-all duration-200 ease-linear md:left-(--sidebar-width) md:group-has-data-[collapsible=icon]/sidebar-wrapper:left-(--sidebar-width-icon) md:group-has-data-[collapsible=offcanvas]/sidebar-wrapper:left-0 md:right-0 left-0 group-has-data-[collapsible=icon]/sidebar-wrapper:h-12">
                    <div className="flex items-center gap-2 px-4">
                        <SidebarTrigger className="-ml-1" />
                        <Separator
                            orientation="vertical"
                            className="mr-2 h-4"
                        />
                        <span className="text-sm text-muted-foreground">
                            Automação Residencial
                        </span>
                    </div>
                </header>

                <main className="flex-1 overflow-auto pt-16">
                    <div className="flex flex-col gap-4 p-4">{children}</div>
                </main>
            </SidebarInset>
        </SidebarProvider>
    );
}
