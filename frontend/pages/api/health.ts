import type { NextApiRequest, NextApiResponse } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  try {
    const upstream = await fetch(`${API_BASE_URL}/api/health`);
    const body = await upstream.text();
    res.setHeader("Content-Type", "application/json");
    res.status(upstream.status).send(body);
  } catch {
    res.status(502).json({
      status: 502,
      message: "The flight data service is unavailable",
      path: req.url ?? "/api/health",
      timestamp: new Date().toISOString(),
    });
  }
}
