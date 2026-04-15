package com.broadcom.demo.ironpasture.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ComplianceToolService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceToolService.class);

    // Static PMO threshold table used by the evaluateSensorThreshold tool
    private static final Map<String, ThresholdEntry> THRESHOLD_TABLE = Map.of(
            "pasteurizationTempF", new ThresholdEntry(161.0, ">=", "PMO Item 16p - HTST: >= 161 degF"),
            "htstHoldTimeSeconds", new ThresholdEntry(15.0, ">=", "PMO Item 16p - HTST hold: >= 15 sec"),
            "rawMilkSomaticCellCount", new ThresholdEntry(750_000, "<=", "PMO Item 7r - SCC: <= 750,000 cells/mL"),
            "processedMilkSpc", new ThresholdEntry(20_000, "<=", "PMO Item 7p - SPC: <= 20,000 cfu/mL"),
            "coliformCount", new ThresholdEntry(10, "<=", "PMO Item 7p - Coliform: <= 10 cfu/mL"),
            "coolerTempF", new ThresholdEntry(45.0, "<=", "PMO Item 16p - Cooler: <= 45 degF"),
            "pH", new ThresholdEntry(7.0, "<=", "PMO - pH max 7.0")
    );

    private record ThresholdEntry(double threshold, String comparator, String pmoReference) {}

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    public ComplianceToolService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Queries the VectorStore for the relevant PMO passage matching the given item code.
     * Returns the section text and citation.
     */
    @Tool(description = "Look up a PMO (Pasteurized Milk Ordinance) standard by item code. Returns the relevant section text and citation.")
    public String lookupPmoStandard(@ToolParam(description = "The PMO item code, e.g. '7p', '7r', '16p'") String itemCode) {
        log.info("Tool call: lookupPmoStandard(itemCode={})", itemCode);

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("PMO item " + itemCode + " standard requirement")
                        .topK(3)
                        .build());

        if (docs.isEmpty()) {
            return "No PMO passage found for item code: " + itemCode;
        }

        var sb = new StringBuilder();
        for (var doc : docs) {
            String section = doc.getMetadata().getOrDefault("section", "unknown").toString();
            String code = doc.getMetadata().getOrDefault("itemCode", "unknown").toString();
            sb.append("[%s / %s] %s\n\n".formatted(section, code, doc.getText()));
        }
        return sb.toString();
    }

    /**
     * Checks the given sensor field value against the embedded PMO threshold table.
     * Returns PASS or FAIL along with the threshold and margin.
     */
    @Tool(description = "Evaluate a sensor reading field against its PMO threshold. Returns PASS/FAIL, the threshold value, and the margin.")
    public String evaluateSensorThreshold(
            @ToolParam(description = "The sensor field name, e.g. 'pasteurizationTempF', 'coliformCount'") String fieldName,
            @ToolParam(description = "The observed numeric value for the field") double value) {

        log.info("Tool call: evaluateSensorThreshold(fieldName={}, value={})", fieldName, value);

        ThresholdEntry entry = THRESHOLD_TABLE.get(fieldName);
        if (entry == null) {
            return """
                    {"fieldName": "%s", "status": "UNKNOWN", "message": "No threshold defined for field"}
                    """.formatted(fieldName);
        }

        boolean pass = switch (entry.comparator()) {
            case ">=" -> value >= entry.threshold();
            case "<=" -> value <= entry.threshold();
            default -> value <= entry.threshold();
        };

        double margin = pass
                ? Math.abs(value - entry.threshold())
                : Math.abs(value - entry.threshold());

        return """
                {"fieldName": "%s", "status": "%s", "observedValue": %.2f, "threshold": %.2f, "margin": %.2f, "pmoReference": "%s"}
                """.formatted(fieldName, pass ? "PASS" : "FAIL", value, entry.threshold(), margin, entry.pmoReference());
    }

    /**
     * Queries silver_sensor_enriched for a 30-day rolling average of the given field
     * at the specified plant.
     */
    // GREENPLUM-OPTIMIZED
    @Tool(description = "Get 30-day historical averages for a sensor field at a given plant. Returns the rolling average value.")
    public String getHistoricalAverages(
            @ToolParam(description = "The plant identifier") String plantId,
            @ToolParam(description = "The sensor field name to average") String fieldName) {

        log.info("Tool call: getHistoricalAverages(plantId={}, fieldName={})", plantId, fieldName);

        // GREENPLUM-OPTIMIZED — this query benefits from Greenplum's columnar storage
        // and distributed execution for large-scale time-series aggregation.
        try {
            String sql = """
                    SELECT AVG(
                        (threshold_status::json -> ? ->> 'observedValue')::numeric
                    ) AS avg_value,
                    COUNT(*) AS sample_count
                    FROM silver_sensor_enriched
                    WHERE plant_id = ?
                      AND enriched_at >= NOW() - INTERVAL '30 days'
                    """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, fieldName, plantId);
            double avgValue = result.get("avg_value") != null
                    ? ((Number) result.get("avg_value")).doubleValue() : 0.0;
            long sampleCount = ((Number) result.get("sample_count")).longValue();

            return """
                    {"plantId": "%s", "fieldName": "%s", "thirtyDayAverage": %.2f, "sampleCount": %d}
                    """.formatted(plantId, fieldName, avgValue, sampleCount);

        } catch (Exception e) {
            log.warn("Historical average query failed for plant={}, field={}: {}", plantId, fieldName, e.getMessage());
            return """
                    {"plantId": "%s", "fieldName": "%s", "thirtyDayAverage": null, "error": "No historical data available"}
                    """.formatted(plantId, fieldName);
        }
    }
}
