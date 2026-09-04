import { SearchForm } from "@/components/search-form";

export default function HomePage() {
  return (
    <div className="flex flex-col gap-8 py-10 sm:py-16">
      <div className="space-y-3">
        <h1 className="max-w-2xl text-display font-semibold">
          How often is your flight actually late?
        </h1>
        <p className="max-w-prose text-body text-muted-foreground">
          On-time rates, delay causes, and the safest departure window for any US domestic flight or
          route — grounded in the federal on-time record, not marketing claims. No login required.
        </p>
      </div>

      <SearchForm />

      <dl className="grid gap-4 border-t pt-6 text-caption sm:grid-cols-3">
        <div>
          <dt className="font-medium">Flight number</dt>
          <dd className="text-muted-foreground">
            Type <span className="font-mono">UA523</span> for one flight&apos;s recent record.
          </dd>
        </div>
        <div>
          <dt className="font-medium">Route</dt>
          <dd className="text-muted-foreground">
            Type <span className="font-mono">SFO-JFK</span> to rank every departure hour.
          </dd>
        </div>
        <div>
          <dt className="font-medium">On time means</dt>
          <dd className="text-muted-foreground">
            Arriving within 15 minutes of schedule — the FAA&apos;s definition.
          </dd>
        </div>
      </dl>
    </div>
  );
}
