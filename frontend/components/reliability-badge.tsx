import { AlertTriangle, CheckCircle2, CircleAlert, Clock, HelpCircle } from "lucide-react";
import type { DelaySeverity } from "@/lib/types";
import { RELIABILITY_LABEL, reliabilityOf } from "@/lib/format";
import { cn } from "@/lib/utils";

// Severity is carried by colour, an icon, AND the label text, never colour alone.
const SEVERITY_STYLES: Record<DelaySeverity, string> = {
  "on-time": "bg-delay-on-time-surface text-delay-on-time",
  minor: "bg-delay-minor-surface text-delay-minor",
  moderate: "bg-delay-moderate-surface text-delay-moderate",
  severe: "bg-delay-severe-surface text-delay-severe",
};

const SEVERITY_ICONS: Record<DelaySeverity, typeof CheckCircle2> = {
  "on-time": CheckCircle2,
  minor: Clock,
  moderate: CircleAlert,
  severe: AlertTriangle,
};

export function ReliabilityBadge({
  onTimeRate,
  className,
}: {
  onTimeRate: number | null;
  className?: string;
}) {
  const severity = reliabilityOf(onTimeRate);

  if (severity === null) {
    return (
      <span
        className={cn(
          "inline-flex items-center gap-1.5 rounded-full bg-muted px-3 py-1 text-caption font-medium text-muted-foreground",
          className,
        )}
      >
        <HelpCircle aria-hidden="true" className="size-4" />
        Not enough data
      </span>
    );
  }

  const Icon = SEVERITY_ICONS[severity];

  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-caption font-medium",
        SEVERITY_STYLES[severity],
        className,
      )}
    >
      <Icon aria-hidden="true" className="size-4" />
      {RELIABILITY_LABEL[severity]}
    </span>
  );
}
