"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";

const TABS = [
  { href: "/", label: "Flight lookup" },
  { href: "/optimal-window", label: "Best time to fly" },
] as const;

export function NavTabs() {
  // usePathname is typed nullable; the root path is the honest fallback.
  const pathname = usePathname() ?? "/";

  return (
    <nav aria-label="Primary" className="flex items-center gap-1">
      {TABS.map((tab) => {
        const isActive =
          tab.href === "/" ? pathname === "/" || pathname.startsWith("/flight") : pathname.startsWith(tab.href);

        return (
          <Link
            key={tab.href}
            href={tab.href}
            aria-current={isActive ? "page" : undefined}
            className={cn(
              "rounded-md px-3 py-1.5 text-caption font-medium transition-colors",
              "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring",
              isActive
                ? "bg-secondary text-secondary-foreground"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            {tab.label}
          </Link>
        );
      })}
    </nav>
  );
}
