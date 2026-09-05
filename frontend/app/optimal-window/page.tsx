import Link from "next/link";
import { ArrowRight, Plane, TrendingDown, TrendingUp } from "lucide-react";
import { HoursChart } from "@/app/optimal-window/hours-chart";
import { ReliabilityBadge } from "@/components/reliability-badge";
import { Button } from "@/components/ui/button";
import { fetchOptimalWindow, isNotFound } from "@/lib/api";
import { MIN_SAMPLE, hhmmToLabel, onTimeRatePercent } from "@/lib/format";
import type { RankedHour } from "@/lib/types";

export const metadata = {
  title: "Best time to fly a route — LateBird",
};

const AIRPORT_PATTERN = /^[A-Za-z]{3}$/;

function hourRange(startHour: number, endHour: number): string {
  return `${hhmmToLabel(startHour * 100)}–${hhmmToLabel(endHour * 100 + 59)}`;
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="space-y-6 py-8">{children}</div>;
}

function EmptyState({ title, body }: { title: string; body: string }) {
  return (
    <section className="rounded-xl border border-dashed p-8 text-center">
      <h1 className="text-title font-semibold">{title}</h1>
      <p className="mx-auto mt-2 max-w-prose text-body text-muted-foreground">
        {body}
      </p>
      <Button className="mt-6" render={<Link href="/" />}>
        Search a route
      </Button>
    </section>
  );
}

function HourRow({
  hour,
  highlight,
}: {
  hour: RankedHour;
  highlight: boolean;
}) {
  return (
    <li className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 py-2">
      <span className="flex items-baseline gap-2">
        <span className="w-6 shrink-0 text-caption tabular-nums text-muted-foreground">
          #{hour.rank}
        </span>
        <span className="font-mono text-body tabular-nums">
          {hhmmToLabel(hour.depHour * 100)}
        </span>
        {highlight && (
          <span className="rounded-full bg-delay-on-time-surface px-2 py-0.5 text-caption font-medium text-delay-on-time">
            In window
          </span>
        )}
      </span>
      <span className="text-caption tabular-nums text-muted-foreground">
        {onTimeRatePercent(hour.onTimeRate)} on time ·{" "}
        {hour.totalFlights.toLocaleString()} flights · avg{" "}
        {hour.avgArrDelay >= 0 ? "+" : ""}
        {Math.round(hour.avgArrDelay)} min
      </span>
    </li>
  );
}

export default async function OptimalWindowPage({
  searchParams,
}: {
  searchParams: Promise<{ origin?: string; dest?: string }>;
}) {
  const { origin, dest } = await searchParams;

  if (
    !origin ||
    !dest ||
    !AIRPORT_PATTERN.test(origin) ||
    !AIRPORT_PATTERN.test(dest)
  ) {
    return (
      <Shell>
        <EmptyState
          title="Pick a route first"
          body="This page ranks every departure hour on a route by how often it lands on time. Pick a route from the search page to see it."
        />
      </Shell>
    );
  }

  const result = await fetchOptimalWindow(
    origin.toUpperCase(),
    dest.toUpperCase(),
  );

  if (!result.ok) {
    // A route with no BTS history is an ordinary answer here, not a broken page.
    if (isNotFound(result.error)) {
      return (
        <Shell>
          <EmptyState
            title={`No record of ${origin.toUpperCase()} → ${dest.toUpperCase()}`}
            body="This tool loads the 30 busiest US airports for March–September 2026, and only routes where both ends are in that set. This pairing falls outside it, or had too few flights to rank."
          />
        </Shell>
      );
    }
    throw new Error(result.error.message);
  }

  const recommendation = result.data;

  // Guard every hour the same way the summary is guarded: no rate below the sample floor.
  const rankedHours = recommendation.hours.filter(
    (hour) => hour.totalFlights >= MIN_SAMPLE,
  );
  const withheldHours = recommendation.hours.length - rankedHours.length;
  const bestHours = rankedHours.slice(0, 5);

  const windowPercent = onTimeRatePercent(recommendation.windowOnTimeRate);
  const routePercent = onTimeRatePercent(recommendation.routeOnTimeRate);
  const liftPoints = Math.round(
    (recommendation.windowOnTimeRate - recommendation.routeOnTimeRate) * 100,
  );

  return (
    <Shell>
      <section
        aria-labelledby="window-headline"
        className="rounded-xl border bg-card p-6 sm:p-8"
      >
        <p className="flex items-center gap-2 text-body text-muted-foreground">
          <span className="font-mono">{recommendation.origin}</span>
          <Plane aria-hidden="true" className="size-4" />
          <span className="font-mono">{recommendation.dest}</span>
        </p>

        {/* The window is the answer to this page's question, so it carries the display size. */}
        <h1 id="window-headline" className="mt-3">
          <span className="block text-caption font-normal text-muted-foreground">
            Depart between
          </span>
          <span className="block text-display font-semibold tabular-nums">
            {hourRange(recommendation.startHour, recommendation.endHour)}
          </span>
        </h1>

        <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-2">
          <p className="text-title font-semibold tabular-nums">{windowPercent}</p>
          <ReliabilityBadge onTimeRate={recommendation.windowOnTimeRate} />
        </div>
        <p className="mt-1 text-caption text-muted-foreground">
          arrive within 15 minutes, across{" "}
          {recommendation.windowFlights.toLocaleString()} flights
        </p>

        <p className="mt-4 flex items-start gap-2 text-body">
          {liftPoints >= 0 ? (
            <TrendingUp
              aria-hidden="true"
              className="mt-0.5 size-4 shrink-0 text-delay-on-time"
            />
          ) : (
            <TrendingDown
              aria-hidden="true"
              className="mt-0.5 size-4 shrink-0 text-delay-severe"
            />
          )}
          <span className="text-muted-foreground">
            That is {Math.abs(liftPoints)} points{" "}
            {liftPoints >= 0 ? "better" : "worse"} than the {routePercent} the
            route manages across all{" "}
            {recommendation.routeFlights.toLocaleString()} flights.
            {recommendation.worstHour !== null &&
              recommendation.worstHourOnTimeRate !== null && (
                <>
                  {" "}
                  The worst hour to leave is{" "}
                  {hhmmToLabel(recommendation.worstHour * 100)}, at{" "}
                  {onTimeRatePercent(recommendation.worstHourOnTimeRate)}.
                </>
              )}
          </span>
        </p>
      </section>

      <section
        aria-labelledby="hours-heading"
        className="rounded-xl border bg-card p-6"
      >
        <h2 id="hours-heading" className="text-title font-semibold">
          Every departure hour, ranked
        </h2>
        <p className="mt-1 max-w-prose text-caption text-muted-foreground">
          Bars show the share of flights arriving within 15 minutes. Solid bars
          fall inside the recommended window; outlined ones do not. Colour
          repeats the reliability rating, which is also written out below.
        </p>

        {rankedHours.length === 0 ? (
          <p className="mt-4 rounded-lg border border-dashed p-6 text-center text-body text-muted-foreground">
            No departure hour on this route has the {MIN_SAMPLE} flights needed
            before an on-time rate means anything.
          </p>
        ) : (
          <>
            <HoursChart
              hours={rankedHours}
              startHour={recommendation.startHour}
              endHour={recommendation.endHour}
            />

            <h3 className="mt-6 text-body font-medium">Best hours to leave</h3>
            <ul className="mt-1 divide-y">
              {bestHours.map((hour) => (
                <HourRow
                  key={hour.depHour}
                  hour={hour}
                  highlight={
                    hour.depHour >= recommendation.startHour &&
                    hour.depHour <= recommendation.endHour
                  }
                />
              ))}
            </ul>

            {withheldHours > 0 && (
              <p className="mt-4 text-caption text-muted-foreground">
                {withheldHours} {withheldHours === 1 ? "hour is" : "hours are"}{" "}
                not shown — fewer than {MIN_SAMPLE} flights on record, which is
                too few to rate.
              </p>
            )}
          </>
        )}
      </section>

      <p className="text-caption text-muted-foreground">
        <Link
          href="/"
          className="inline-flex items-center gap-1 underline underline-offset-4"
        >
          Look up a specific flight instead
          <ArrowRight aria-hidden="true" className="size-3.5" />
        </Link>
      </p>
    </Shell>
  );
}
