"use client";

import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";

export function ThemeToggle() {
  const { resolvedTheme, setTheme } = useTheme();
  // The server cannot know the visitor's theme, so wait for mount before naming it.
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);

  const isDark = mounted && resolvedTheme === "dark";

  return (
    <Button
      size="icon"
      variant="ghost"
      aria-label={
        mounted ? (isDark ? "Switch to light theme" : "Switch to dark theme") : "Switch theme"
      }
      onClick={() => setTheme(isDark ? "light" : "dark")}
    >
      {isDark ? <Moon aria-hidden="true" /> : <Sun aria-hidden="true" />}
    </Button>
  );
}
