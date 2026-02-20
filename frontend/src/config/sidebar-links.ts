import {
    Snowflake,
    Lightbulb,
    type LucideIcon,
} from "lucide-react";

export interface NavSubItem {
    title: string;
    url: string;
}

export interface NavItem {
    title: string;
    url: string;
    icon?: LucideIcon;
    isActive?: boolean;
    items?: NavSubItem[];
}

export interface NavGroup {
    title: string;
    items: NavItem[];
}

export const SIDEBAR_ITEMS: NavGroup[] = [
    {
        title: "",
        items: [
            {
                title: "Ar Condicionado",
                url: "/dashboard/ar-condicionado",
                icon: Snowflake,
            },
        ],
    },
    {
        title: "",
        items: [
            {
                title: "Iluminação",
                url: "/dashboard/iluminacao",
                icon: Lightbulb,
            },
        ],
    },
];
