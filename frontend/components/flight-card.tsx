import { Plane } from "lucide-react";
import { ReliabilityBadge } from "@/components/reliability-badge";
import { MIN_SAMPLE, hasEnoughSample, onTimeRatePercent } from "@/lib/format";
import type { FlightAnalysisResponse } from "@/lib/types";

export function FlightCard({ flight }: { flight: FlightAnalysisResponse }) {
  const percent = onTimeRatePercent(flight.onTimeRate);
  const enoughSample = hasEnoughSample(flight.completedOperations);

  return (
    <section aria-labelledby="flight-headline" className="rounded-xl border bg-card p-6 sm:p-8">
      {/* Identity is context for the answer, so it sits above it as a smaller eyebrow. */}
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-body text-muted-foreground">
        <span className="font-mono font-semibold text-foreground">{flight.flightNumber}</span>
        {flight.carrierName && <span>{flight.carrierName}</span>}
        {flight.origin && flight.dest && (
          <span className="flex items-center gap-2">
            <span className="font-mono">{flight.origin}</span>
            <Plane aria-hidden="true" className="size-4" />
            <span className="font-mono">{flight.dest}</span>
          </span>
        )}
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-3">
        {percent === null ? (
          <h1 id="flight-headline" className="text-title font-semibold">
            Not enough data yet
          </h1>
        ) : (
          // Both spans live inside the h1 so heading navigation reads the full sentence.
          <h1 id="flight-headline">
            <span className="block text-display font-semibold tabular-nums">{percent}</span>
            <span className="block text-caption font-normal text-muted-foreground">
              arrived within 15 minutes
            </span>
          </h1>
        )}

        <ReliabilityBadge onTimeRate={flight.onTimeRate} />
      </div>

      {percent === null && (
        <p className="mt-3 max-w-prose text-body text-muted-foreground">
          Only {flight.completedOperations} completed{" "}
          {flight.completedOperations === 1 ? "flight" : "flights"} on record — fewer than the{" "}
          {MIN_SAMPLE} needed before an on-time rate means anything.
        </p>
      )}

      <dl className="mt-6 grid grid-cols-2 gap-4 border-t pt-4 text-caption sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">Operations on record</dt>
          <dd className="font-medium tabular-nums">{flight.totalOperations}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Completed</dt>
          <dd className="font-medium tabular-nums">{flight.completedOperations}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Cancelled</dt>
          <dd className="font-medium tabular-nums">
            {flight.totalOperations - flight.completedOperations}
          </dd>
        </div>
      </dl>

      {!enoughSample && flight.totalOperations > 0 && (
        <p className="mt-4 text-caption text-muted-foreground">
          Individual operations are still listed below — only the summary rate is withheld.
        </p>
      )}
    </section>
  );
}
