export interface NavItem {
    title: string;
    url: string;
}

export interface NavGroup {
    title: string;
    items: NavItem[];
}

export const SIDEBAR_ITEMS: NavGroup[] = [
    {
        title: "",
        items: [
            { title: "Casa", url: "/dashboard" },
            { title: "Luzes", url: "/dashboard/iluminacao" },
            { title: "Ar", url: "/dashboard/ar-condicionado" },
        ],
    },
];
