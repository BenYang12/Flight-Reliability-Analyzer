import type { NextApiRequest, NextApiResponse } from "next";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8081";

export default async function handler(req: NextApiRequest, res: NextApiResponse) {
  const { origin, dest } = req.query;
  if (typeof origin !== "string" || typeof dest !== "string") {
    res.status(400).json({
      status: 400,
      message: "origin and dest are both required",
      path: req.url ?? "/api/optimal-window",
      timestamp: new Date().toISOString(),
    });
    return;
  }

  const query = new URLSearchParams({ origin, dest });
  try {
    const upstream = await fetch(`${API_BASE_URL}/api/optimal-window?${query}`);
    const body = await upstream.text();
    res.setHeader("Content-Type", "application/json");
    res.status(upstream.status).send(body);
  } catch {
    res.status(502).json({
      status: 502,
      message: "Route reliability is unavailable",
      path: req.url ?? "/api/optimal-window",
      timestamp: new Date().toISOString(),
    });
  }
}
