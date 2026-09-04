import { aggregateDelayCauses } from "@/lib/format";
import type { FlightOperation } from "@/lib/types";

export function DelayBreakdown({ operations }: { operations: FlightOperation[] }) {
  const causes = aggregateDelayCauses(operations);

  if (causes.length === 0) {
    return (
      <section aria-labelledby="causes-heading" className="rounded-xl border bg-card p-6">
        <h2 id="causes-heading" className="text-title font-semibold">
          Why it runs late
        </h2>
        <p className="mt-2 max-w-prose text-body text-muted-foreground">
          No delay causes were filed across these operations — either they ran close enough to
          schedule that no cause was reportable, or the carrier reported none.
        </p>
      </section>
    );
  }

  const totalMinutes = causes.reduce((sum, cause) => sum + cause.minutes, 0);

  return (
    <section aria-labelledby="causes-heading" className="rounded-xl border bg-card p-6">
      <h2 id="causes-heading" className="text-title font-semibold">
        Why it runs late
      </h2>
      <p className="mt-1 text-caption text-muted-foreground">
        {totalMinutes.toLocaleString()} delay minutes across {operations.length} operations, as filed
        by the carrier with the Bureau of Transportation Statistics.
      </p>

      <ul className="mt-5 space-y-4">
        {causes.map((cause) => {
          const percent = Math.round(cause.share * 100);

          return (
            <li key={cause.key}>
              <div className="flex items-baseline justify-between gap-4 text-body">
                <span className="font-medium">{cause.label}</span>
                <span className="tabular-nums text-muted-foreground">
                  {percent}% · {cause.minutes.toLocaleString()} min
                </span>
              </div>
              {/* The bar repeats the number beside it, so it is decoration, not the only signal. */}
              <div
                aria-hidden="true"
                className="mt-1.5 h-2 overflow-hidden rounded-full bg-muted"
              >
                <div className="h-full rounded-full bg-foreground/60" style={{ width: `${percent}%` }} />
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
