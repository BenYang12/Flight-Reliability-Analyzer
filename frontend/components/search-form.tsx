"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, Loader2, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { COVERED_AIRPORTS, airportLabel } from "@/lib/airports";
import { searchQuery } from "@/lib/api";

// Free-tier backends cold-start slowly, so say so rather than looking hung.
const COLD_START_HINT_MS = 6000;

// Matches the Input primitive so the two controls line up in the same row.
const SELECT_CLASS =
  "h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-base transition-colors outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 md:text-sm dark:bg-input/30";

export function SearchForm() {
  const router = useRouter();
  const [query, setQuery] = useState("");
  const [origin, setOrigin] = useState("SFO");
  const [dest, setDest] = useState("JFK");
  const [isSearching, setIsSearching] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [showColdStartHint, setShowColdStartHint] = useState(false);

  // A real side effect: a timer, not a value derivable from existing state.
  useEffect(() => {
    if (!isSearching) {
      setShowColdStartHint(false);
      return;
    }
    const timer = setTimeout(() => setShowColdStartHint(true), COLD_START_HINT_MS);
    return () => clearTimeout(timer);
  }, [isSearching]);

  async function handleFlightSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const trimmed = query.trim();
    if (trimmed === "") return;

    setIsSearching(true);
    setErrorMessage(null);

    const result = await searchQuery(trimmed);

    if (!result.ok) {
      setIsSearching(false);
      setErrorMessage(
        result.error.status === 0
          ? "Could not reach the flight data service. It may still be starting up."
          : result.error.message,
      );
      return;
    }

    const { type, flightNumber, origin: foundOrigin, dest: foundDest } = result.data;

    if (type === "FLIGHT" && flightNumber) {
      // isSearching stays true so the button remains disabled through navigation.
      router.push(`/flight/${encodeURIComponent(flightNumber)}`);
      return;
    }

    if (foundOrigin && foundDest) {
      router.push(`/optimal-window?${new URLSearchParams({ origin: foundOrigin, dest: foundDest })}`);
      return;
    }

    setIsSearching(false);
    setErrorMessage("That query resolved to neither a flight nor a route.");
  }

  // Both codes come from the covered list, so this route always has data.
  function handleRouteSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    router.push(`/optimal-window?${new URLSearchParams({ origin, dest })}`);
  }

  return (
    <div className="w-full max-w-xl">
      <Tabs defaultValue="route">
        <TabsList>
          <TabsTrigger value="route">Route</TabsTrigger>
          <TabsTrigger value="flight">Flight number</TabsTrigger>
        </TabsList>

        <TabsContent value="route">
          <form onSubmit={handleRouteSubmit} className="mt-3">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-end">
              <div className="flex-1">
                <Label htmlFor="origin" className="text-caption text-muted-foreground">
                  From
                </Label>
                <select
                  id="origin"
                  value={origin}
                  onChange={(event) => setOrigin(event.target.value)}
                  className={`mt-1 ${SELECT_CLASS}`}
                >
                  {COVERED_AIRPORTS.map((airport) => (
                    <option key={airport.iata} value={airport.iata}>
                      {airportLabel(airport)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="flex-1">
                <Label htmlFor="dest" className="text-caption text-muted-foreground">
                  To
                </Label>
                <select
                  id="dest"
                  value={dest}
                  onChange={(event) => setDest(event.target.value)}
                  className={`mt-1 ${SELECT_CLASS}`}
                >
                  {COVERED_AIRPORTS.map((airport) => (
                    <option key={airport.iata} value={airport.iata}>
                      {airportLabel(airport)}
                    </option>
                  ))}
                </select>
              </div>

              <Button type="submit" disabled={origin === dest}>
                <Search aria-hidden="true" className="size-4" />
                Search
              </Button>
            </div>

            <p className="mt-3 min-h-6 text-caption text-muted-foreground">
              {origin === dest
                ? "Pick two different airports."
                : `Covers the ${COVERED_AIRPORTS.length} busiest US airports, March–September 2026.`}
            </p>
          </form>
        </TabsContent>

        <TabsContent value="flight">
          <form onSubmit={handleFlightSubmit} className="mt-3">
            <Label htmlFor="flight-query" className="text-caption text-muted-foreground">
              Flight number
            </Label>

            <div className="mt-1 flex flex-col gap-2 sm:flex-row">
              <Input
                id="flight-query"
                name="q"
                value={query}
                onChange={(event) => setQuery(event.target.value.toUpperCase())}
                placeholder="UA654"
                autoComplete="off"
                spellCheck={false}
                disabled={isSearching}
                aria-describedby={
                  errorMessage ? "flight-query-error" : "flight-query-hint"
                }
                aria-invalid={errorMessage !== null}
                className="font-mono"
              />

              <Button type="submit" disabled={isSearching || query.trim() === ""}>
                {isSearching ? (
                  <Loader2 aria-hidden="true" className="size-4 animate-spin" />
                ) : (
                  <Search aria-hidden="true" className="size-4" />
                )}
                {isSearching ? "Searching" : "Search"}
              </Button>
            </div>

            {/* Free text cannot be constrained the way the route picker is, so say what is covered. */}
            <p id="flight-query-hint" className="mt-2 text-caption text-muted-foreground">
              Only flights between the {COVERED_AIRPORTS.length} busiest US airports, March–September
              2026. Try <span className="font-mono">UA654</span> or{" "}
              <span className="font-mono">AA777</span>.
            </p>

            {/* aria-live so a screen reader announces results that appear after submit. */}
            <div aria-live="polite" className="mt-2 min-h-6">
              {errorMessage && (
                <p
                  id="flight-query-error"
                  role="alert"
                  className="flex items-start gap-2 text-caption text-delay-severe"
                >
                  <AlertCircle aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
                  <span>{errorMessage}</span>
                </p>
              )}

              {isSearching && showColdStartHint && !errorMessage && (
                <p className="text-caption text-muted-foreground">
                  Still working — the data service may be waking up, which can take up to a minute.
                </p>
              )}
            </div>
          </form>
        </TabsContent>
      </Tabs>
    </div>
  );
}
