"use client";

import * as React from "react";

interface LogoBrandProps extends React.SVGProps<SVGSVGElement> {
    className?: string;
    animateOnMount?: boolean;
    animationType?: "fade" | "slide";
}

export function LogoBrand({ 
    className, 
    animateOnMount = true,
    animationType = "fade",
    ...props 
}: LogoBrandProps) {
    const [isVisible, setIsVisible] = React.useState(!animateOnMount);

    React.useEffect(() => {
        if (animateOnMount) {
            const timer = setTimeout(() => {
                setIsVisible(true);
            }, 50);
            return () => clearTimeout(timer);
        }
    }, [animateOnMount]);

    const animationClass = React.useMemo(() => {
        if (!isVisible) return "opacity-0";
        return animationType === "fade" 
            ? "animate-logo-fade-in" 
            : "animate-logo-slide-in";
    }, [isVisible, animationType]);

    return (
        <div className={`${animationClass} flex items-center justify-center shrink-0 ${className || ""}`}>
            <div className="size-full rounded-lg bg-primary/10 flex items-center justify-center aspect-square">
                <span className="text-xs font-bold text-primary leading-none whitespace-nowrap">AR</span>
            </div>
        </div>
    );
}
