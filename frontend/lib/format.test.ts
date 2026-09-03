import { describe, expect, it } from "vitest";
import {
  aggregateDelayCauses,
  formatDelay,
  hasEnoughSample,
  hhmmToLabel,
  onTimeRatePercent,
  severityOf,
} from "./format";
import type { FlightOperation } from "./types";

function operation(overrides: Partial<FlightOperation>): FlightOperation {
  return {
    flightDate: "2024-03-11",
    origin: "SFO",
    dest: "JFK",
    carrierName: "United Air Lines Inc.",
    crsDepTime: 1435,
    depTime: 1440,
    crsArrTime: 2300,
    arrTime: 2310,
    crsElapsedTime: 325,
    depDelayMin: 5,
    arrDelayMin: 10,
    taxiOut: 20,
    taxiIn: 8,
    distance: 2586,
    dayOfWeek: 1,
    month: 3,
    carrierDelay: null,
    weatherDelay: null,
    nasDelay: null,
    securityDelay: null,
    lateAircraftDelay: null,
    cancelled: false,
    diverted: false,
    onTime: true,
    analysis: null,
    ...overrides,
  };
}

describe("severityOf", () => {
  it("treats the FAA 15-minute line as on time, inclusive", () => {
    expect(severityOf(15)).toBe("on-time");
    expect(severityOf(16)).toBe("minor");
  });

  it("treats an early arrival as on time", () => {
    expect(severityOf(-20)).toBe("on-time");
  });

  it("returns null for an unknown delay rather than guessing", () => {
    expect(severityOf(null)).toBeNull();
  });

  it("escalates through the buckets", () => {
    expect(severityOf(45)).toBe("minor");
    expect(severityOf(46)).toBe("moderate");
    expect(severityOf(120)).toBe("moderate");
    expect(severityOf(121)).toBe("severe");
  });
});

describe("formatDelay", () => {
  it("distinguishes early, late, and unknown", () => {
    expect(formatDelay(-12)).toBe("12 min early");
    expect(formatDelay(34)).toBe("34 min late");
    expect(formatDelay(0)).toBe("Exactly on schedule");
    expect(formatDelay(null)).toBe("Unknown");
  });
});

describe("hhmmToLabel", () => {
  it("reads BTS integer times as clock times", () => {
    expect(hhmmToLabel(1435)).toBe("14:35");
    expect(hhmmToLabel(5)).toBe("00:05");
    expect(hhmmToLabel(2400)).toBe("24:00");
    expect(hhmmToLabel(null)).toBe("—");
  });
});

describe("hasEnoughSample", () => {
  it("matches the backend sample floor of 10", () => {
    expect(hasEnoughSample(9)).toBe(false);
    expect(hasEnoughSample(10)).toBe(true);
  });
});

describe("onTimeRatePercent", () => {
  it("converts the 0-1 fraction to a whole percent", () => {
    expect(onTimeRatePercent(0.833)).toBe("83%");
    expect(onTimeRatePercent(1)).toBe("100%");
  });

  it("passes null through so callers cannot print a suppressed rate", () => {
    expect(onTimeRatePercent(null)).toBeNull();
  });
});

describe("aggregateDelayCauses", () => {
  it("sums cause minutes and ranks them by share", () => {
    const causes = aggregateDelayCauses([
      operation({ carrierDelay: 10, lateAircraftDelay: 30 }),
      operation({ carrierDelay: 5, nasDelay: 40 }),
    ]);

    expect(causes.map((cause) => cause.key)).toEqual(["nas", "lateAircraft", "carrier"]);
    expect(causes[0].minutes).toBe(40);
    expect(causes[0].share).toBeCloseTo(40 / 85);
  });

  it("returns nothing when no operation reported a cause", () => {
    expect(aggregateDelayCauses([operation({})])).toEqual([]);
  });
});
