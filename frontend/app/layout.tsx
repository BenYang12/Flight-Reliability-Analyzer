import type { Metadata } from "next";
import Link from "next/link";
import { Geist, Geist_Mono, Space_Grotesk } from "next/font/google";
import { Plane } from "lucide-react";
import { Background } from "@/components/background";
import { NavTabs } from "@/components/nav-tab";
import { ThemeProvider } from "@/components/theme-provider";
import "./globals.css";

const geistSans = Geist({ variable: "--font-sans", subsets: ["latin"] });
const geistMono = Geist_Mono({ variable: "--font-geist-mono", subsets: ["latin"] });
// Headings only, so the display face never blocks body text from painting.
const spaceGrotesk = Space_Grotesk({ variable: "--font-display", subsets: ["latin"] });

export const metadata: Metadata = {
  title: "LateBird — how often is your flight actually late?",
  description:
    "On-time rates, delay causes, and the safest departure window for any US domestic flight or route, grounded in the federal on-time record. No login required.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    // suppressHydrationWarning: next-themes sets the theme class before React hydrates.
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} ${spaceGrotesk.variable} min-h-dvh antialiased`}
      >
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <Background />
          <div className="mx-auto flex min-h-dvh w-full max-w-5xl flex-col px-4 sm:px-6">
            <header className="flex flex-wrap items-center justify-between gap-3 py-5">
              <Link
                href="/"
                className="flex items-center gap-2 font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
              >
                <Plane aria-hidden="true" className="size-5" />
                <span>LateBird</span>
              </Link>
              <NavTabs />
            </header>

            <main className="flex-1 pb-16">{children}</main>

            <footer className="border-t py-6 text-caption text-muted-foreground">
              Statistics from the US Bureau of Transportation Statistics. Live operations from OpenSky.
            </footer>
          </div>
        </ThemeProvider>
      </body>
    </html>
  );
}
