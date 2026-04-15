package com.broadcom.demo.ironpasture.silver;

import com.broadcom.demo.ironpasture.model.ThresholdResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SilverEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(SilverEnrichmentService.class);

    // PMO threshold table: fieldName -> (maxValue, pmoReference)
    // "less-than-or-equal" thresholds — observed must be <= threshold to PASS
    private static final Map<String, ThresholdSpec> THRESHOLDS = Map.of(
            "pasteurizationTempF", new ThresholdSpec(161.0, ">=", "PMO Item 16p - HTST pasteurization: >= 161 degF"),
            "htstHoldTimeSeconds", new ThresholdSpec(15.0, ">=", "PMO Item 16p - HTST hold time: >= 15 seconds"),
            "rawMilkSomaticCellCount", new ThresholdSpec(750_000, "<=", "PMO Item 7r - Raw milk SCC: <= 750,000 cells/mL"),
            "processedMilkSpc", new ThresholdSpec(20_000, "<=", "PMO Item 7p - Pasteurized milk SPC: <= 20,000 cfu/mL"),
            "coliformCount", new ThresholdSpec(10, "<=", "PMO Item 7p - Coliform count: <= 10 cfu/mL"),
            "coolerTempF", new ThresholdSpec(45.0, "<=", "PMO Item 16p - Cooler temperature: <= 45 degF"),
            "pH", new ThresholdSpec(7.0, "<=", "PMO - pH: expected range 6.4-6.8, max 7.0")
    );

    private record ThresholdSpec(double thresholdValue, String comparator, String pmoReference) {}

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public SilverEnrichmentService(JdbcTemplate jdbcTemplate, VectorStore vectorStore, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the bronze record, evaluates each field against PMO thresholds,
     * retrieves matching PMO passages for failed fields from VectorStore,
     * and writes the enriched record to silver_sensor_enriched.
     *
     * @return the silver enrichment ID
     */
    public long enrichSensorReading(long bronzeId) {
        Map<String, Object> bronze = jdbcTemplate.queryForMap(
                "SELECT * FROM bronze_sensor_readings WHERE id = ?", bronzeId);

        Map<String, ThresholdResult> results = new LinkedHashMap<>();

        results.put("pasteurizationTempF", evaluateThreshold("pasteurizationTempF",
                ((Number) bronze.get("pasteurization_temp_f")).doubleValue()));
        results.put("htstHoldTimeSeconds", evaluateThreshold("htstHoldTimeSeconds",
                ((Number) bronze.get("htst_hold_time_seconds")).doubleValue()));
        results.put("rawMilkSomaticCellCount", evaluateThreshold("rawMilkSomaticCellCount",
                ((Number) bronze.get("raw_milk_somatic_cell_count")).doubleValue()));
        results.put("processedMilkSpc", evaluateThreshold("processedMilkSpc",
                ((Number) bronze.get("processed_milk_spc")).doubleValue()));
        results.put("coliformCount", evaluateThreshold("coliformCount",
                ((Number) bronze.get("coliform_count")).doubleValue()));
        results.put("coolerTempF", evaluateThreshold("coolerTempF",
                ((Number) bronze.get("cooler_temp_f")).doubleValue()));
        results.put("pH", evaluateThreshold("pH",
                ((Number) bronze.get("ph")).doubleValue()));

        // Phosphatase is a pass/fail string test
        String phosphataseValue = (String) bronze.get("phosphatase_test");
        boolean phosphatasePass = "negative".equalsIgnoreCase(phosphataseValue);
        results.put("phosphataseTest", new ThresholdResult(
                "phosphataseTest",
                phosphatasePass ? "PASS" : "FAIL",
                phosphatasePass ? 0.0 : 1.0,
                0.0,
                0.0,
                "PMO Item 16p - Phosphatase test: must be negative"
        ));

        // Retrieve PMO passages for any failed fields
        List<String> failedFields = results.entrySet().stream()
                .filter(e -> "FAIL".equals(e.getValue().status()))
                .map(Map.Entry::getKey)
                .toList();

        List<String> pmoPassages = new ArrayList<>();
        if (!failedFields.isEmpty()) {
            String searchQuery = "PMO compliance thresholds for " +
                    failedFields.stream().collect(Collectors.joining(", "));
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder().query(searchQuery).topK(5).build());
            pmoPassages = docs.stream().map(Document::getText).toList();
        }

        // Persist to silver_sensor_enriched
        try {
            String thresholdJson = objectMapper.writeValueAsString(results);
            String passagesJson = objectMapper.writeValueAsString(pmoPassages);

            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO silver_sensor_enriched (
                            bronze_id, plant_id, batch_id, threshold_status, pmo_passages, enriched_at
                        ) VALUES (?, ?, ?, ?::jsonb, ?::jsonb, NOW())
                        """, Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, bronzeId);
                ps.setString(2, (String) bronze.get("plant_id"));
                ps.setString(3, (String) bronze.get("batch_id"));
                ps.setString(4, thresholdJson);
                ps.setString(5, passagesJson);
                return ps;
            }, keyHolder);

            long silverId = keyHolder.getKey().longValue();
            log.info("Enriched sensor reading: bronzeId={} -> silverId={}, failures={}",
                    bronzeId, silverId, failedFields);
            return silverId;

        } catch (Exception e) {
            throw new RuntimeException("Failed to persist silver enrichment for bronzeId=" + bronzeId, e);
        }
    }

    /**
     * Evaluates a single field value against its PMO threshold.
     */
    private ThresholdResult evaluateThreshold(String fieldName, double observedValue) {
        ThresholdSpec spec = THRESHOLDS.get(fieldName);
        if (spec == null) {
            return new ThresholdResult(fieldName, "PASS", observedValue, 0.0, 0.0, "No threshold defined");
        }

        boolean pass = switch (spec.comparator()) {
            case ">=" -> observedValue >= spec.thresholdValue();
            case "<=" -> observedValue <= spec.thresholdValue();
            default -> observedValue <= spec.thresholdValue();
        };

        if (pass) {
            return ThresholdResult.pass(fieldName, observedValue, spec.thresholdValue(), spec.pmoReference());
        } else {
            return ThresholdResult.fail(fieldName, observedValue, spec.thresholdValue(), spec.pmoReference());
        }
    }
}
