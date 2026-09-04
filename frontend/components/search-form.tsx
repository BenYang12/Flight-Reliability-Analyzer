"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { AlertCircle, Loader2, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { searchQuery } from "@/lib/api";

// Free-tier backends cold-start slowly, so say so rather than looking hung.
const COLD_START_HINT_MS = 6000;

export function SearchForm() {
  const router = useRouter();
  const [query, setQuery] = useState("");
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

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
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

    const { type, flightNumber, origin, dest } = result.data;

    if (type === "FLIGHT" && flightNumber) {
      // isSearching stays true so the button remains disabled through navigation.
      router.push(`/flight/${encodeURIComponent(flightNumber)}`);
      return;
    }

    if (origin && dest) {
      const params = new URLSearchParams({ origin, dest });
      router.push(`/optimal-window?${params}`);
      return;
    }

    setIsSearching(false);
    setErrorMessage("That query resolved to neither a flight nor a route.");
  }

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-xl">
      <Label htmlFor="flight-query" className="text-caption text-muted-foreground">
        Flight number or route
      </Label>

      <div className="mt-2 flex flex-col gap-2 sm:flex-row">
        <Input
          id="flight-query"
          name="q"
          value={query}
          onChange={(event) => setQuery(event.target.value.toUpperCase())}
          placeholder="UA523  or  SFO-JFK"
          autoComplete="off"
          spellCheck={false}
          disabled={isSearching}
          aria-describedby={errorMessage ? "flight-query-error" : undefined}
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

      {/* aria-live so a screen reader announces results that appear after submit. */}
      <div aria-live="polite" className="mt-3 min-h-6">
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
  );
}
