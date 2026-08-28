import { apiFetch } from "../lib/api-client";

/** POST /ml/segment (ml-engine, proxied directly by nginx / the Vite dev server). */
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
  // Segmentation loads a model on first use; give it longer than the default budget.
  return apiFetch<SegmentationResponse>("/ml/segment", { method: "POST", body: request, timeoutMs: 60_000 });
}
