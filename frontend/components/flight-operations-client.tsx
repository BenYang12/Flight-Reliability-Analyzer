"use client";

import { useReducer } from "react";
import { Ban, ChevronDown, HelpCircle, Sparkles } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SEVERITY_ICONS, SEVERITY_STYLES } from "@/components/reliability-badge";
import { SEVERITY_LABEL, formatDelay, hhmmToLabel, severityOf } from "@/lib/format";
import type { DelaySeverity, FlightOperation } from "@/lib/types";
import { cn } from "@/lib/utils";

type Filter = "all" | DelaySeverity | "cancelled";
type Sort = "date-desc" | "date-asc" | "delay-desc" | "delay-asc";

interface State {
  filter: Filter;
  sort: Sort;
  expandedKey: string | null;
}

type Action =
  | { type: "set-filter"; filter: Filter }
  | { type: "set-sort"; sort: Sort }
  | { type: "toggle-row"; key: string };

const INITIAL_STATE: State = { filter: "all", sort: "date-desc", expandedKey: null };

// Changing the filter collapses the open row, because that row may no longer be listed.
function reducer(state: State, action: Action): State {
  switch (action.type) {
    case "set-filter":
      return { ...state, filter: action.filter, expandedKey: null };
    case "set-sort":
      return { ...state, sort: action.sort, expandedKey: null };
    case "toggle-row":
      return {
        ...state,
        expandedKey: state.expandedKey === action.key ? null : action.key,
      };
  }
}

const FILTER_LABEL: Record<Filter, string> = {
  all: "All",
  "on-time": "On time",
  minor: "Minor",
  moderate: "Moderate",
  severe: "Severe",
  cancelled: "Cancelled",
};

const FILTER_ORDER: Filter[] = ["all", "on-time", "minor", "moderate", "severe", "cancelled"];

const SORT_LABEL: Record<Sort, string> = {
  "date-desc": "Newest first",
  "date-asc": "Oldest first",
  "delay-desc": "Most delayed",
  "delay-asc": "Least delayed",
};

const MONTHS = [
  "Jan", "Feb", "Mar", "Apr", "May", "Jun",
  "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
];

// Split the ISO string by hand rather than via Date, which would shift the day across timezones.
function formatFlightDate(isoDate: string): string {
  const [year, month, day] = isoDate.split("-");
  const monthName = MONTHS[Number(month) - 1];
  if (!monthName) return isoDate;
  return `${monthName} ${Number(day)} ${year}`;
}

function matchesFilter(operation: FlightOperation, filter: Filter): boolean {
  if (filter === "all") return true;
  if (filter === "cancelled") return operation.cancelled === true;
  if (operation.cancelled === true) return false;
  return severityOf(operation.arrDelayMin) === filter;
}

// Cancelled and unflown operations have no delay to rank, so they sort to the bottom either way.
function compare(a: FlightOperation, b: FlightOperation, sort: Sort): number {
  if (sort === "date-desc") return b.flightDate.localeCompare(a.flightDate);
  if (sort === "date-asc") return a.flightDate.localeCompare(b.flightDate);

  const left = a.arrDelayMin;
  const right = b.arrDelayMin;
  if (left === null && right === null) return 0;
  if (left === null) return 1;
  if (right === null) return -1;
  return sort === "delay-desc" ? right - left : left - right;
}

function DelayChip({ operation }: { operation: FlightOperation }) {
  if (operation.cancelled === true) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-caption font-medium text-muted-foreground">
        <Ban aria-hidden="true" className="size-3.5" />
        Cancelled
      </span>
    );
  }

  const severity = severityOf(operation.arrDelayMin);

  if (severity === null) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-muted px-2.5 py-1 text-caption font-medium text-muted-foreground">
        <HelpCircle aria-hidden="true" className="size-3.5" />
        No arrival recorded
      </span>
    );
  }

  const Icon = SEVERITY_ICONS[severity];

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-caption font-medium",
        SEVERITY_STYLES[severity],
      )}
    >
      <Icon aria-hidden="true" className="size-3.5" />
      <span className="sr-only">{SEVERITY_LABEL[severity]}: </span>
      {formatDelay(operation.arrDelayMin)}
    </span>
  );
}

function Detail({ term, value }: { term: string; value: string }) {
  return (
    <div>
      <dt className="text-caption text-muted-foreground">{term}</dt>
      <dd className="text-body tabular-nums">{value}</dd>
    </div>
  );
}

function CauseList({ operation }: { operation: FlightOperation }) {
  const causes = [
    { label: "Airline", minutes: operation.carrierDelay },
    { label: "Weather", minutes: operation.weatherDelay },
    { label: "Air traffic control", minutes: operation.nasDelay },
    { label: "Security", minutes: operation.securityDelay },
    { label: "Late inbound aircraft", minutes: operation.lateAircraftDelay },
  ].filter((cause) => (cause.minutes ?? 0) > 0);

  if (causes.length === 0) return null;

  return (
    <div className="mt-4">
      <h3 className="text-caption font-medium text-muted-foreground">Causes filed by the carrier</h3>
      <ul className="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-body">
        {causes.map((cause) => (
          <li key={cause.label} className="tabular-nums">
            {cause.label} <span className="text-muted-foreground">{cause.minutes} min</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function AnalysisPanel({ operation }: { operation: FlightOperation }) {
  const analysis = operation.analysis;

  if (analysis === null) {
    return (
      <p className="mt-4 text-body text-muted-foreground">
        No plain-English analysis for this operation — the model service was unavailable when this
        page was built. The figures above are unaffected.
      </p>
    );
  }

  return (
    <div className="mt-4 rounded-lg bg-muted/50 p-4">
      <h3 className="flex items-center gap-2 text-body font-medium">
        <Sparkles aria-hidden="true" className="size-4" />
        {analysis.archetype}
      </h3>
      <p className="mt-1 max-w-prose text-body text-muted-foreground">{analysis.summary}</p>
      {analysis.facts.length > 0 && (
        <ul className="mt-3 list-inside list-disc space-y-1 text-caption text-muted-foreground">
          {analysis.facts.map((fact) => (
            <li key={fact}>{fact}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function FlightOperationsClient({ operations }: { operations: FlightOperation[] }) {
  const [state, dispatch] = useReducer(reducer, INITIAL_STATE);

  // Derived from props and state on every render, so there is nothing to keep in sync.
  const counts = FILTER_ORDER.map((filter) => ({
    filter,
    count: operations.filter((operation) => matchesFilter(operation, filter)).length,
  }));

  const visible = operations
    .filter((operation) => matchesFilter(operation, state.filter))
    .sort((a, b) => compare(a, b, state.sort));

  return (
    <section aria-labelledby="operations-heading" className="rounded-xl border bg-card p-6">
      <h2 id="operations-heading" className="text-title font-semibold">
        Every operation on record
      </h2>
      <p className="mt-1 max-w-prose text-caption text-muted-foreground">
        Each row is one scheduled departure as filed with the Bureau of Transportation Statistics.
        Expand a row for its full record and the model&apos;s reading of it.
      </p>

      <div className="mt-5 flex flex-wrap items-center justify-between gap-4">
        <div role="group" aria-label="Filter operations" className="flex flex-wrap gap-1.5">
          {counts.map(({ filter, count }) => (
            <Button
              key={filter}
              size="sm"
              variant={state.filter === filter ? "secondary" : "ghost"}
              aria-pressed={state.filter === filter}
              disabled={count === 0 && filter !== "all"}
              onClick={() => dispatch({ type: "set-filter", filter })}
            >
              {FILTER_LABEL[filter]}
              <span className="tabular-nums text-muted-foreground">{count}</span>
            </Button>
          ))}
        </div>

        <div className="flex items-center gap-2">
          <label htmlFor="operations-sort" className="text-caption text-muted-foreground">
            Sort
          </label>
          <select
            id="operations-sort"
            value={state.sort}
            onChange={(event) => dispatch({ type: "set-sort", sort: event.target.value as Sort })}
            className="h-8 rounded-lg border bg-background px-2 text-caption"
          >
            {(Object.keys(SORT_LABEL) as Sort[]).map((sort) => (
              <option key={sort} value={sort}>
                {SORT_LABEL[sort]}
              </option>
            ))}
          </select>
        </div>
      </div>

      <p aria-live="polite" className="mt-3 text-caption text-muted-foreground">
        Showing {visible.length} of {operations.length} operations.
      </p>

      {visible.length === 0 ? (
        <p className="mt-4 rounded-lg border border-dashed p-6 text-center text-body text-muted-foreground">
          No operations match this filter.
        </p>
      ) : (
        <ul className="mt-3 space-y-2">
          {visible.map((operation, index) => {
            const key = `${operation.flightDate}-${operation.crsDepTime ?? index}`;
            const expanded = state.expandedKey === key;
            const panelId = `operation-panel-${key}`;

            return (
              <li key={key} className="rounded-lg border">
                {/* Below sm this stacks into a card; from sm up it is a single row. */}
                <div className="flex flex-col gap-2 p-3 sm:flex-row sm:flex-wrap sm:items-center sm:gap-x-4">
                  <div className="flex items-center justify-between gap-3 sm:flex-none">
                    <span className="text-body tabular-nums sm:w-28">
                      {formatFlightDate(operation.flightDate)}
                    </span>
                    <DelayChip operation={operation} />
                  </div>

                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-caption text-muted-foreground">
                    <span className="font-mono">
                      {operation.origin ?? "???"} → {operation.dest ?? "???"}
                    </span>
                    <span className="tabular-nums">
                      {hhmmToLabel(operation.crsDepTime)} → {hhmmToLabel(operation.crsArrTime)}
                    </span>
                  </div>

                  <Button
                    size="sm"
                    variant="ghost"
                    className="w-full justify-center sm:ml-auto sm:w-auto"
                    aria-expanded={expanded}
                    aria-controls={panelId}
                    onClick={() => dispatch({ type: "toggle-row", key })}
                  >
                    Details
                    <ChevronDown
                      aria-hidden="true"
                      className={cn("transition-transform", expanded && "rotate-180")}
                    />
                  </Button>
                </div>

                {expanded && (
                  <div id={panelId} className="border-t p-4">
                    <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
                      <Detail term="Scheduled departure" value={hhmmToLabel(operation.crsDepTime)} />
                      <Detail term="Actual departure" value={hhmmToLabel(operation.depTime)} />
                      <Detail term="Scheduled arrival" value={hhmmToLabel(operation.crsArrTime)} />
                      <Detail term="Actual arrival" value={hhmmToLabel(operation.arrTime)} />
                      <Detail term="Departure delay" value={formatDelay(operation.depDelayMin)} />
                      <Detail term="Arrival delay" value={formatDelay(operation.arrDelayMin)} />
                      <Detail
                        term="Taxi out / in"
                        value={`${operation.taxiOut ?? "—"} / ${operation.taxiIn ?? "—"} min`}
                      />
                      <Detail
                        term="Distance"
                        value={operation.distance === null ? "—" : `${operation.distance} mi`}
                      />
                    </dl>

                    <CauseList operation={operation} />
                    <AnalysisPanel operation={operation} />
                  </div>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
