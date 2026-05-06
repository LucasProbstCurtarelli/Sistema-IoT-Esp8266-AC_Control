import type { NextConfig } from "next";

const backendProxy =
    process.env.BACKEND_PROXY_TARGET?.replace(/\/$/, "") || "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
    output: "standalone",
    reactStrictMode: true,
    async rewrites() {
        return {
            afterFiles: [
                { source: "/api/devices/:path*", destination: `${backendProxy}/api/devices/:path*` },
                { source: "/api/lights/:path*", destination: `${backendProxy}/api/lights/:path*` },
                { source: "/api/ac/:path*", destination: `${backendProxy}/api/ac/:path*` },
                { source: "/api/health/:path*", destination: `${backendProxy}/api/health/:path*` },
                { source: "/api/login", destination: `${backendProxy}/api/login` },
                { source: "/api/logout", destination: `${backendProxy}/api/logout` },
                { source: "/api/me", destination: `${backendProxy}/api/me` },
                { source: "/api/admin/:path*", destination: `${backendProxy}/api/admin/:path*` },
            ],
        };
    },
    async headers() {
        return [
            {
                source: "/:path*",
                headers: [
                    { key: "X-Frame-Options", value: "DENY" },
                    { key: "X-Content-Type-Options", value: "nosniff" },
                    { key: "Referrer-Policy", value: "no-referrer" },
                    {
                        key: "Permissions-Policy",
                        value: "camera=(), microphone=(), geolocation=()",
                    },
                ],
            },
        ];
    },
};

export default nextConfig;
