package com.broadcom.demo.ironpasture.model;

import java.time.Instant;

public record SensorReading(
        String plantId,
        Instant readingTimestamp,
        double pasteurizationTempF,
        double htstHoldTimeSeconds,
        int rawMilkSomaticCellCount,
        int processedMilkSpc,
        int coliformCount,
        double pH,
        double coolerTempF,
        String phosphataseTest,
        String operatorId,
        String batchId
) {}
