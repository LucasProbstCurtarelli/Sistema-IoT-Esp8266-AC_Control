import { HouseNav } from "@/components/app-sidebar";
import { DashboardAuthGate } from "@/components/dashboard-auth-gate";

export default function DashboardLayout({
    children,
}: {
    children: React.ReactNode;
}) {
    return (
        <DashboardAuthGate>
            <div className="min-h-svh bg-background text-foreground">
                <HouseNav />
                <main className="px-5 pb-28 pt-2 md:ml-40 md:px-12 md:pb-16 md:pt-14">
                    <div className="max-w-[28rem]">{children}</div>
                </main>
            </div>
        </DashboardAuthGate>
    );
}
