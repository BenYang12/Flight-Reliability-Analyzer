import type { NextApiRequest, NextApiResponse } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const { flightNumber, page = "0", size = "20" } = req.query;
  if (typeof flightNumber !== "string" || flightNumber.trim() === "") {
    res.status(400).json({
      status: 400,
      message: "flightNumber is required",
      path: req.url ?? "/api/operations",
      timestamp: new Date().toISOString(),
    });
    return;
  }

  const query = new URLSearchParams({ page: String(page), size: String(size) });
  try {
    const upstream = await fetch(
      `${API_BASE_URL}/api/flights/${encodeURIComponent(flightNumber.trim())}/operations?${query}`,
    );
    const body = await upstream.text();
    res.setHeader("Content-Type", "application/json");
    res.status(upstream.status).send(body);
  } catch {
    res.status(502).json({
      status: 502,
      message: "Live operations are unavailable",
      path: req.url ?? "/api/operations",
      timestamp: new Date().toISOString(),
    });
  }
}
