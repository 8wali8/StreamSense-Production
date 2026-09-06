import { env } from "../config/env";
import { apiFetch } from "../lib/api-client";

/**
 * POST /ml/segment. ml-engine is reached through nginx (Docker) or the Vite proxy (dev), never through
 * the gateway, so these calls use the ML base URL rather than the API base.
 */
export type SegmentationRequest = {
  frameId: string;
  frameRef: string;
};

export type RegionProposal = {
  label: string;
  confidence: number;
  x: number;
  y: number;
  width: number;
  height: number;
  source: string;
  areaRatio: number;
};

export type SegmentationResponse = {
  modelVersion: string;
  frameWidth: number;
  frameHeight: number;
  proposals: RegionProposal[];
};

export function segmentFrame(request: SegmentationRequest): Promise<SegmentationResponse> {
  // Segmentation loads a model on first use. With the SAM backend on a fresh volume, the first call
  // downloads the checkpoint and initialises the model before running CPU inference, which can take
  // well over a minute; the budget must outlast that cold start (nginx and the gateway allow the same).
  return apiFetch<SegmentationResponse>("/ml/segment", {
    method: "POST",
    body: request,
    timeoutMs: 180_000,
    baseUrl: env.mlBaseUrl,
  });
}
