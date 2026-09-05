/**
 * The 30 airports the BTS import actually covers.
 *
 * This mirrors TOP_30 in ml/get_data.py, which filters the corpus to flights
 * where BOTH endpoints are in this set. A route touching any other airport has
 * no rows at all, so offering it in the UI would only produce a dead end.
 */
export type CoveredAirport = { iata: string; city: string };

export const COVERED_AIRPORTS: CoveredAirport[] = [
  { iata: "ATL", city: "Atlanta" },
  { iata: "AUS", city: "Austin" },
  { iata: "BNA", city: "Nashville" },
  { iata: "BOS", city: "Boston" },
  { iata: "BWI", city: "Baltimore" },
  { iata: "CLT", city: "Charlotte" },
  { iata: "DCA", city: "Washington" },
  { iata: "DEN", city: "Denver" },
  { iata: "DFW", city: "Dallas-Fort Worth" },
  { iata: "DTW", city: "Detroit" },
  { iata: "EWR", city: "Newark" },
  { iata: "FLL", city: "Fort Lauderdale" },
  { iata: "IAD", city: "Dulles" },
  { iata: "IAH", city: "Houston" },
  { iata: "JFK", city: "New York" },
  { iata: "LAS", city: "Las Vegas" },
  { iata: "LAX", city: "Los Angeles" },
  { iata: "LGA", city: "New York" },
  { iata: "MCO", city: "Orlando" },
  { iata: "MDW", city: "Chicago" },
  { iata: "MIA", city: "Miami" },
  { iata: "MSP", city: "Minneapolis" },
  { iata: "ORD", city: "Chicago" },
  { iata: "PHL", city: "Philadelphia" },
  { iata: "PHX", city: "Phoenix" },
  { iata: "SAN", city: "San Diego" },
  { iata: "SEA", city: "Seattle" },
  { iata: "SFO", city: "San Francisco" },
  { iata: "SLC", city: "Salt Lake City" },
  { iata: "TPA", city: "Tampa" },
];

// Four cities own two airports each, so the code has to disambiguate the label.
export function airportLabel({ iata, city }: CoveredAirport): string {
  return `${city} (${iata})`;
}
