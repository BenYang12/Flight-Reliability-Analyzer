import type { NextApiRequest, NextApiResponse } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const { q } = req.query;
  if (typeof q !== "string" || q.trim() === "") {
    res.status(400).json({
      status: 400,
      message: "Enter a flight number like UA523 or a route like SFO-JFK",
      path: req.url ?? "/api/search",
      timestamp: new Date().toISOString(),
    });
    return;
  }

  const query = new URLSearchParams({ q: q.trim() });
  try {
    const upstream = await fetch(`${API_BASE_URL}/api/search?${query}`);
    const body = await upstream.text();
    res.setHeader("Content-Type", "application/json");
    res.status(upstream.status).send(body);
  } catch {
    res.status(502).json({
      status: 502,
      message: "The flight data service is unavailable",
      path: req.url ?? "/api/search",
      timestamp: new Date().toISOString(),
    });
  }
}
