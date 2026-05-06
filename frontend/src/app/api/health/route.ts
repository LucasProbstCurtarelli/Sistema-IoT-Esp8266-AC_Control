import { NextResponse } from "next/server";

/** Docker / load balancer healthcheck (no auth). */
export function GET() {
    return NextResponse.json({ status: "ok" }, { status: 200 });
}
