"use client";

import {
  Bar,
  BarChart,
  Cell,
  ResponsiveContainer,
  XAxis,
  YAxis,
} from "recharts";
import { hhmmToLabel, reliabilityOf } from "@/lib/format";
import type { DelaySeverity, RankedHour } from "@/lib/types";

// Recharts writes these straight into SVG fill, so they must be raw values, not Tailwind classes.
const SEVERITY_FILL: Record<DelaySeverity, string> = {
  "on-time": "var(--delay-on-time)",
  minor: "var(--delay-minor)",
  moderate: "var(--delay-moderate)",
  severe: "var(--delay-severe)",
};

export function HoursChart({
  hours,
  startHour,
  endHour,
}: {
  hours: RankedHour[];
  startHour: number;
  endHour: number;
}) {
  const data = [...hours]
    .sort((a, b) => a.depHour - b.depHour)
    .map((hour) => ({
      depHour: hour.depHour,
      percent: Math.round(hour.onTimeRate * 100),
      severity: reliabilityOf(hour.onTimeRate),
      inWindow: hour.depHour >= startHour && hour.depHour <= endHour,
    }));

  return (
    // The chart scrolls inside this container so the page itself never scrolls sideways.
    <div className="mt-4 overflow-x-auto">
      <div className="h-64 min-w-[36rem]">
        {/* aria-hidden because the same ranking is printed as text below. */}
        <ResponsiveContainer width="100%" height="100%">
          <BarChart
            data={data}
            margin={{ top: 8, right: 8, bottom: 0, left: -16 }}
            aria-hidden
          >
            <XAxis
              dataKey="depHour"
              tickFormatter={(hour: number) => hhmmToLabel(hour * 100)}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
            />
            <YAxis
              domain={[0, 100]}
              tickFormatter={(value: number) => `${value}%`}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 11, fill: "var(--muted-foreground)" }}
            />
            <Bar
              dataKey="percent"
              radius={[4, 4, 0, 0]}
              isAnimationActive={false}
            >
              {data.map((entry) => (
                <Cell
                  key={entry.depHour}
                  fill={
                    entry.severity === null
                      ? "var(--muted)"
                      : SEVERITY_FILL[entry.severity]
                  }
                  fillOpacity={entry.inWindow ? 1 : 0.35}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
