import { notFound } from "next/navigation";
import { Info } from "lucide-react";
import { DelayBreakdown } from "@/components/delay-breakdown";
import { FlightCard } from "@/components/flight-card";
import { fetchFlight, isNotFound } from "@/lib/api";

// In Next 15 App Router, params arrives as a Promise and must be awaited.
export default async function FlightPage({
  params,
}: {
  params: Promise<{ flightNumber: string }>;
}) {
  const { flightNumber } = await params;
  const result = await fetchFlight(flightNumber);

  if (!result.ok) {
    if (isNotFound(result.error)) notFound();
    // Anything else is genuinely broken, so let error.tsx handle it.
    throw new Error(result.error.message);
  }

  const flight = result.data;

  // Flask being cold nulls every analysis while leaving the statistics intact.
  const analysisUnavailable =
    flight.operations.length > 0 && flight.operations.every((op) => op.analysis === null);

  return (
    <div className="space-y-6 py-8">
      <FlightCard flight={flight} />

      {analysisUnavailable && (
        <div className="flex items-start gap-3 rounded-xl border border-dashed p-4 text-body">
          <Info aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-muted-foreground" />
          <p className="text-muted-foreground">
            Plain-English analysis is unavailable right now — the model service may still be waking
            up. Every statistic on this page is unaffected, because they come from the federal record
            rather than the model.
          </p>
        </div>
      )}

      <DelayBreakdown operations={flight.operations} />
    </div>
  );
}
