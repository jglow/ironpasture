package com.broadcom.demo.ironpasture.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record NcimsReport(
        String facilityName,
        String plantId,
        LocalDate inspectionDate,
        String inspectorId,
        String regulatoryAuthority,
        ThresholdResult item16p_pasteurizationTemp,
        ThresholdResult item16p_holdTime,
        ThresholdResult item7r_scc,
        ThresholdResult item7p_spc,
        ThresholdResult item7p_coliform,
        ThresholdResult item16p_phosphatase,
        ThresholdResult coolerTempCompliance,
        ComplianceDisposition overallDisposition,
        String llmDraftNarrative,
        List<String> pmoPassagesReferenced,
        Instant auditTimestamp,
        String modelUsed,
        String promptVersion,
        String batchId,
        long silverEnrichmentId
) {}
