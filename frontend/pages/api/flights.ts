import type { NextApiRequest, NextApiResponse } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const { flightNumber } = req.query;
  if (typeof flightNumber !== "string" || flightNumber.trim() === "") {
    res.status(400).json({
      status: 400,
      message: "flightNumber is required",
      path: req.url ?? "/api/flights",
      timestamp: new Date().toISOString(),
    });
    return;
  }

  try {
    const upstream = await fetch(
      `${API_BASE_URL}/api/flights/${encodeURIComponent(flightNumber.trim())}`,
    );
    const body = await upstream.text();
    res.setHeader("Content-Type", "application/json");
    res.status(upstream.status).send(body);
  } catch {
    res.status(502).json({
      status: 502,
      message: "The flight data service is unavailable",
      path: req.url ?? "/api/flights",
      timestamp: new Date().toISOString(),
    });
  }
}
