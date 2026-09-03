export type DelaySeverity = "on-time" | "minor" | "moderate" | "severe";

// GET /api/search

export type SearchType = "FLIGHT" | "ROUTE";

export interface SearchResponse {
  type: SearchType;
  /** Set for FLIGHT, null for ROUTE. */
  flightNumber: string | null;
  /** Set for both — a flight number resolves to its usual route. */
  origin: string | null;
  dest: string | null;
  operationCount: number;
}

// The Flask analyzer's verdict (AnalyzeServiceDto.Result)
export interface AnalyzeResult {
  cluster: number;
  archetype: string;
  description: string;
  onTime: boolean;
  /** The rule-derived phrase list. The LLM phrases these; it never invents them. */
  facts: string[];
  summary: string;
}

// GET /api/flights/{flightNumber}
export interface FlightOperation {
  flightDate: string;
  origin: string | null;
  dest: string | null;
  carrierName: string | null;

  /** Times are HHMM integers, so 1435 means 14:35. Not minutes, not a timestamp. */
  crsDepTime: number | null;
  depTime: number | null;
  crsArrTime: number | null;
  arrTime: number | null;
  /** Scheduled gate-to-gate minutes. */
  crsElapsedTime: number | null;

  /** Signed: negative means early. BTS's DepDelay, not DepDelayMinutes. */
  depDelayMin: number | null;
  arrDelayMin: number | null;

  taxiOut: number | null;
  taxiIn: number | null;
  distance: number | null;
  dayOfWeek: number | null;
  month: number | null;

  /** The carriers' own mandatory cause filings, in minutes. */
  carrierDelay: number | null;
  weatherDelay: number | null;
  nasDelay: number | null;
  securityDelay: number | null;
  lateAircraftDelay: number | null;

  cancelled: boolean | null;
  diverted: boolean | null;
  onTime: boolean | null;
  analysis: AnalyzeResult | null;
}

export interface FlightAnalysisResponse {
  flightNumber: string;
  carrierName: string | null;
  origin: string | null;
  dest: string | null;
  totalOperations: number;
  completedOperations: number;
  onTimeRate: number | null;
  operations: FlightOperation[];
}

// GET /api/flights/{flightNumber}/operations  — live OpenSky
// recent actual operations
export interface FlightOperationLive {
  flightNumber: string;
  callsign: string | null;
  flightDate: string;
  // IATA form, resolved from ICAO: "SFO"
  origin: string | null;
  dest: string | null;
  /** ISO-8601 instant strings. */
  departedAt: string | null;
  arrivedAt: string | null;
  actualAirborneMinutes: number | null;
}

// GET /api/routes/{origin}/{dest} and GET /api/reliability
export interface HourlyReliability {
  depHour: number;
  totalFlights: number;
  onTimeCount: number;
  onTimeRate: number;
  avgArrDelay: number;
  p90ArrDelay: number | null;
  cancelRate: number | null;
}

export interface RouteReliabilityResponse {
  origin: string;
  dest: string;
  carrierIata: string | null;
  totalFlights: number;
  onTimeCount: number;
  onTimeRate: number;
  avgArrDelay: number;
  hours: HourlyReliability[];
}

// GET /api/optimal-window
export interface RankedHour {
  // 1 = best on-time rate.
  rank: number;
  depHour: number;
  totalFlights: number;
  onTimeRate: number;
  avgArrDelay: number;
}

export interface OptimalWindowResponse {
  origin: string;
  dest: string;
  /** The recommended departure window, inclusive hours. */
  startHour: number;
  endHour: number;
  windowFlights: number;
  windowOnTimeRate: number;
  windowAvgArrDelay: number;
  /** Route-wide baseline, so the window has something to beat. */
  routeFlights: number;
  routeOnTimeRate: number;
  /** Null when the route has only one qualifying hour — no contrast to draw. */
  worstHour: number | null;
  worstHourOnTimeRate: number | null;
  hours: RankedHour[];
}

// Reference lookups
export interface Airport {
  icao: string | null;
  iata: string | null;
  name: string | null;
  city: string | null;
  lat: number | null;
  lon: number | null;
}

export interface Carrier {
  icao: string;
  iata: string;
  name: string;
}

// Errors
export interface ErrorResponse {
  status: number;
  message: string;
  path: string;
  timestamp: string;
}
