package com.streamsense.analyticsservice.model;

/** One sponsor's exposure in one bucket, with the box area of its accepted detections for prominence. */
public record SponsorBucketExposure(
        long bucketStart, long detectionCount, long acceptedDetectionCount, long estimatedExposureMs, double areaSum) {}
