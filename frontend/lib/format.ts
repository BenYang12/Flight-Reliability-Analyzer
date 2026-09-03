import type { DelaySeverity, FlightOperation } from "@/lib/types";

export const MIN_SAMPLE = 10;

export const ON_TIME_THRESHOLD_MIN = 15;
const MINOR_THRESHOLD_MIN = 45;
const MODERATE_THRESHOLD_MIN = 120;

export const SEVERITY_LABEL: Record<DelaySeverity, string> = {
  "on-time": "On time",
  minor: "Minor delay",
  moderate: "Moderate delay",
  severe: "Severe delay",
};

export function severityOf(arrDelayMin: number | null): DelaySeverity | null {
  if (arrDelayMin === null) return null;
  if (arrDelayMin <= ON_TIME_THRESHOLD_MIN) return "on-time";
  if (arrDelayMin <= MINOR_THRESHOLD_MIN) return "minor";
  if (arrDelayMin <= MODERATE_THRESHOLD_MIN) return "moderate";
  return "severe";
}

export function formatDelay(arrDelayMin: number | null): string {
  if (arrDelayMin === null) return "Unknown";
  if (arrDelayMin === 0) return "Exactly on schedule";
  if (arrDelayMin < 0) return `${Math.abs(arrDelayMin)} min early`;
  return `${arrDelayMin} min late`;
}

export function hhmmToLabel(hhmm: number | null): string {
  if (hhmm === null) return "—";
  const hours = Math.floor(hhmm / 100);
  const minutes = hhmm % 100;
  if (hours > 24 || minutes > 59) return "—";
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

export function hasEnoughSample(completedOperations: number): boolean {
  return completedOperations >= MIN_SAMPLE;
}

export function onTimeRatePercent(rate: number | null): string | null {
  if (rate === null) return null;
  return `${Math.round(rate * 100)}%`;
}

export interface DelayCause {
  key: "carrier" | "weather" | "nas" | "security" | "lateAircraft";
  label: string;
  minutes: number;
  share: number;
}

const CAUSE_LABELS: Record<DelayCause["key"], string> = {
  carrier: "Airline",
  weather: "Weather",
  nas: "Air traffic control",
  security: "Security",
  lateAircraft: "Late inbound aircraft",
};

export function aggregateDelayCauses(operations: FlightOperation[]): DelayCause[] {
  const totals: Record<DelayCause["key"], number> = {
    carrier: 0,
    weather: 0,
    nas: 0,
    security: 0,
    lateAircraft: 0,
  };

  for (const operation of operations) {
    totals.carrier += operation.carrierDelay ?? 0;
    totals.weather += operation.weatherDelay ?? 0;
    totals.nas += operation.nasDelay ?? 0;
    totals.security += operation.securityDelay ?? 0;
    totals.lateAircraft += operation.lateAircraftDelay ?? 0;
  }

  const grandTotal = Object.values(totals).reduce((sum, value) => sum + value, 0);
  if (grandTotal === 0) return [];

  return (Object.keys(totals) as DelayCause["key"][])
    .map((key) => ({
      key,
      label: CAUSE_LABELS[key],
      minutes: totals[key],
      share: totals[key] / grandTotal,
    }))
    .filter((cause) => cause.minutes > 0)
    .sort((a, b) => b.minutes - a.minutes);
}
