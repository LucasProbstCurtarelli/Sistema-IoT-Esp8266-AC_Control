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
        <div className={`${animationClass} flex items-center justify-center ${className || ""}`}>
            <div className="w-10 h-10 rounded-lg bg-primary/10 flex items-center justify-center">
                <span className="text-lg font-bold text-primary">AR</span>
            </div>
        </div>
    );
}
