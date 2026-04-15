package com.broadcom.demo.ironpasture.controller;

import com.broadcom.demo.ironpasture.bronze.BronzeIngestionService;
import com.broadcom.demo.ironpasture.gold.GoldReportService;
import com.broadcom.demo.ironpasture.ingest.PmoIngestService;
import com.broadcom.demo.ironpasture.model.NcimsReport;
import com.broadcom.demo.ironpasture.model.SensorReading;
import com.broadcom.demo.ironpasture.silver.SilverEnrichmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    private final BronzeIngestionService bronzeIngestionService;
    private final SilverEnrichmentService silverEnrichmentService;
    private final GoldReportService goldReportService;
    private final PmoIngestService pmoIngestService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.chat.options.model:#{null}}")
    private String ollamaModel;

    @Value("${spring.ai.openai.chat.options.model:#{null}}")
    private String openaiModel;

    public ComplianceController(BronzeIngestionService bronzeIngestionService,
                                SilverEnrichmentService silverEnrichmentService,
                                GoldReportService goldReportService,
                                PmoIngestService pmoIngestService,
                                JdbcTemplate jdbcTemplate,
                                ObjectMapper objectMapper) {
        this.bronzeIngestionService = bronzeIngestionService;
        this.silverEnrichmentService = silverEnrichmentService;
        this.goldReportService = goldReportService;
        this.pmoIngestService = pmoIngestService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Accepts a SensorReading, runs the full Bronze -> Silver -> Gold pipeline,
     * and returns the generated NCIMS report.
     */
    @PostMapping("/api/compliance/pre-fill")
    public ResponseEntity<?> preFill(@RequestBody SensorReading reading) {
        try {
            log.info("Pre-fill request received for batchId={}", reading.batchId());

            // Bronze: ingest raw sensor data
            long bronzeId = bronzeIngestionService.ingestSensorReading(reading);

            // Silver: enrich with threshold evaluation and PMO passages
            long silverId = silverEnrichmentService.enrichSensorReading(bronzeId);

            // Gold: generate NCIMS report via LLM with tools
            NcimsReport report = goldReportService.generateReport(silverId);

            return ResponseEntity.ok(report);
        } catch (Exception e) {
            log.error("Pre-fill pipeline failed for batchId={}", reading.batchId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Compliance pipeline failed",
                            "message", e.getMessage(),
                            "batchId", reading.batchId()
                    ));
        }
    }

    /**
     * Retrieves a gold report by batch ID.
     */
    @GetMapping("/api/compliance/report/{batchId}")
    public ResponseEntity<?> getReport(@PathVariable String batchId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT * FROM gold_ncims_reports WHERE batch_id = ? ORDER BY audit_timestamp DESC LIMIT 1",
                    batchId);

            if (results.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "No report found for batchId: " + batchId));
            }

            return ResponseEntity.ok(results.getFirst());
        } catch (Exception e) {
            log.error("Failed to retrieve report for batchId={}", batchId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve report", "message", e.getMessage()));
        }
    }

    /**
     * Returns the last 30 gold reports for a given plant.
     */
    @GetMapping("/api/compliance/history/{plantId}")
    public ResponseEntity<?> getHistory(@PathVariable String plantId) {
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                    "SELECT * FROM gold_ncims_reports WHERE plant_id = ? ORDER BY audit_timestamp DESC LIMIT 30",
                    plantId);

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to retrieve history for plantId={}", plantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to retrieve history", "message", e.getMessage()));
        }
    }

    /**
     * Triggers manual PMO re-ingestion.
     */
    @PostMapping("/api/admin/ingest/pmo")
    public ResponseEntity<?> reingestPmo() {
        try {
            int count = pmoIngestService.reingest();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "chunksIngested", count
            ));
        } catch (Exception e) {
            log.error("PMO re-ingestion failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "PMO re-ingestion failed", "message", e.getMessage()));
        }
    }

    /**
     * Returns application info: model in use, chunks indexed, platform.
     */
    @GetMapping("/api/info")
    public ResponseEntity<?> info() {
        String model = ollamaModel != null ? ollamaModel : (openaiModel != null ? openaiModel : "not configured");
        String platform = System.getProperty("os.name", "unknown");

        int chunksIndexed = 0;
        try {
            chunksIndexed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bronze_pmo_chunks", Integer.class);
        } catch (Exception ignored) {
            // Table may not exist yet
        }

        return ResponseEntity.ok(Map.of(
                "application", "Iron Pasture - NCIMS Compliance Pre-Fill",
                "model", model,
                "chunksIndexed", chunksIndexed,
                "platform", platform,
                "javaVersion", System.getProperty("java.version", "unknown")
        ));
    }
}
