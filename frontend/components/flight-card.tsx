import { Plane } from "lucide-react";
import { ReliabilityBadge } from "@/components/reliability-badge";
import { MIN_SAMPLE, hasEnoughSample, onTimeRatePercent } from "@/lib/format";
import type { FlightAnalysisResponse } from "@/lib/types";

export function FlightCard({ flight }: { flight: FlightAnalysisResponse }) {
  const percent = onTimeRatePercent(flight.onTimeRate);
  const enoughSample = hasEnoughSample(flight.completedOperations);

  return (
    <section aria-labelledby="flight-headline" className="rounded-xl border bg-card p-6 sm:p-8">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h1 id="flight-headline" className="font-mono text-title font-semibold">
          {flight.flightNumber}
        </h1>
        {flight.carrierName && (
          <span className="text-body text-muted-foreground">{flight.carrierName}</span>
        )}
      </div>

      {flight.origin && flight.dest && (
        <p className="mt-1 flex items-center gap-2 text-body text-muted-foreground">
          <span className="font-mono">{flight.origin}</span>
          <Plane aria-hidden="true" className="size-4" />
          <span className="font-mono">{flight.dest}</span>
        </p>
      )}

      <div className="mt-6 flex flex-wrap items-end gap-x-6 gap-y-3">
        {percent === null ? (
          <p className="max-w-prose text-body">
            Only {flight.completedOperations} completed{" "}
            {flight.completedOperations === 1 ? "flight" : "flights"} on record — fewer than the{" "}
            {MIN_SAMPLE} needed before an on-time rate means anything.
          </p>
        ) : (
          <div>
            <p className="text-display font-semibold tabular-nums">{percent}</p>
            <p className="text-caption text-muted-foreground">arrived within 15 minutes</p>
          </div>
        )}

        <ReliabilityBadge onTimeRate={flight.onTimeRate} className="mb-1" />
      </div>

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
