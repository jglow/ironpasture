package com.broadcom.demo.ironpasture.gold;

import com.broadcom.demo.ironpasture.agent.ComplianceToolService;
import com.broadcom.demo.ironpasture.model.ComplianceDisposition;
import com.broadcom.demo.ironpasture.model.NcimsReport;
import com.broadcom.demo.ironpasture.model.ThresholdResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GoldReportService {

    private static final Logger log = LoggerFactory.getLogger(GoldReportService.class);
    private static final String PROMPT_VERSION = "v1.0";

    private final ChatModel chatModel;
    private final ComplianceToolService complianceToolService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.ollama.chat.options.model:#{null}}")
    private String ollamaModel;

    @Value("${spring.ai.openai.chat.options.model:#{null}}")
    private String openaiModel;

    public GoldReportService(ChatModel chatModel,
                             ComplianceToolService complianceToolService,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.complianceToolService = complianceToolService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads the silver enrichment record, constructs a ChatModel call with compliance tools,
     * parses the LLM response, maps to NcimsReport, and persists to gold_ncims_reports.
     */
    public NcimsReport generateReport(long silverEnrichmentId) {
        Map<String, Object> silver = jdbcTemplate.queryForMap(
                "SELECT * FROM silver_sensor_enriched WHERE id = ?", silverEnrichmentId);

        String plantId = (String) silver.get("plant_id");
        String batchId = (String) silver.get("batch_id");
        String thresholdStatusJson = silver.get("threshold_status").toString();
        String pmoPassagesJson = silver.get("pmo_passages") != null
                ? silver.get("pmo_passages").toString() : "[]";

        try {
            Map<String, ThresholdResult> thresholdResults = objectMapper.readValue(
                    thresholdStatusJson, new TypeReference<>() {});

            // Build the system + user prompt
            String systemPrompt = """
                    You are a dairy compliance analyst AI. Your job is to review sensor readings
                    from a milk processing plant and generate an NCIMS Form 2359 compliance narrative.

                    You have access to tools that let you:
                    1. Look up PMO standards by item code
                    2. Evaluate sensor thresholds against PMO limits
                    3. Get historical 30-day averages for comparison

                    Use the tools to verify each threshold and cite the relevant PMO passages.
                    Then produce a compliance narrative summarizing findings.

                    Respond with a JSON object containing:
                    - "disposition": one of "COMPLIANT", "NON_COMPLIANT", "REQUIRES_REVIEW"
                    - "narrative": a detailed narrative for the NCIMS form
                    - "pmoReferences": an array of PMO passage citations used
                    """;

            String userPrompt = """
                    Review the following sensor data for plant %s, batch %s:

                    Threshold evaluation results:
                    %s

                    Relevant PMO passages retrieved:
                    %s

                    Please use your tools to verify the thresholds, look up any relevant PMO standards,
                    check historical averages, and then generate the compliance narrative.
                    """.formatted(plantId, batchId, thresholdStatusJson, pmoPassagesJson);

            // Register tools using MethodToolCallbackProvider
            var toolCallbackProvider = MethodToolCallbackProvider.builder()
                    .toolObjects(complianceToolService)
                    .build();

            ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();

            var chatOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(toolCallbacks)
                    .build();

            var prompt = new Prompt(
                    List.of(
                            new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                            new org.springframework.ai.chat.messages.UserMessage(userPrompt)
                    ),
                    chatOptions
            );

            ChatResponse response = chatModel.call(prompt);
            String llmOutput = response.getResult().getOutput().getText();

            // Parse LLM response
            String narrative;
            ComplianceDisposition disposition;
            List<String> pmoReferences;

            try {
                var parsed = objectMapper.readValue(llmOutput, Map.class);
                narrative = (String) parsed.getOrDefault("narrative", llmOutput);
                disposition = ComplianceDisposition.valueOf(
                        ((String) parsed.getOrDefault("disposition", "REQUIRES_REVIEW")).toUpperCase());
                var refs = parsed.get("pmoReferences");
                pmoReferences = refs instanceof List<?> list
                        ? list.stream().map(Object::toString).toList()
                        : List.of();
            } catch (Exception parseEx) {
                // LLM didn't return valid JSON — use raw text as narrative
                log.warn("Could not parse LLM response as JSON, using raw text as narrative");
                narrative = llmOutput;
                disposition = determineDispositionFromThresholds(thresholdResults);
                pmoReferences = List.of();
            }

            String modelUsed = ollamaModel != null ? ollamaModel : (openaiModel != null ? openaiModel : "unknown");

            NcimsReport report = new NcimsReport(
                    "Plant " + plantId,
                    plantId,
                    LocalDate.now(),
                    "AI-GENERATED",
                    "FDA/State PMO",
                    thresholdResults.get("pasteurizationTempF"),
                    thresholdResults.get("htstHoldTimeSeconds"),
                    thresholdResults.get("rawMilkSomaticCellCount"),
                    thresholdResults.get("processedMilkSpc"),
                    thresholdResults.get("coliformCount"),
                    thresholdResults.get("phosphataseTest"),
                    thresholdResults.get("coolerTempF"),
                    disposition,
                    narrative,
                    pmoReferences,
                    Instant.now(),
                    modelUsed,
                    PROMPT_VERSION,
                    batchId
            );

            persistReport(report);
            log.info("Generated gold report for batchId={}, disposition={}", batchId, disposition);
            return report;

        } catch (Exception e) {
            log.error("LLM report generation failed for silverEnrichmentId={}", silverEnrichmentId, e);
            return generateFallbackReport(silver, silverEnrichmentId);
        }
    }

    private NcimsReport generateFallbackReport(Map<String, Object> silver, long silverEnrichmentId) {
        String plantId = (String) silver.get("plant_id");
        String batchId = (String) silver.get("batch_id");

        String modelUsed = ollamaModel != null ? ollamaModel : (openaiModel != null ? openaiModel : "unknown");

        NcimsReport fallback = new NcimsReport(
                "Plant " + plantId,
                plantId,
                LocalDate.now(),
                "AI-GENERATED",
                "FDA/State PMO",
                null, null, null, null, null, null, null,
                ComplianceDisposition.REQUIRES_REVIEW,
                "Automated compliance analysis could not be completed. Manual review required. " +
                        "Silver enrichment ID: " + silverEnrichmentId,
                List.of(),
                Instant.now(),
                modelUsed,
                PROMPT_VERSION,
                batchId
        );

        persistReport(fallback);
        return fallback;
    }

    private ComplianceDisposition determineDispositionFromThresholds(Map<String, ThresholdResult> results) {
        boolean anyFail = results.values().stream()
                .anyMatch(r -> "FAIL".equals(r.status()));
        return anyFail ? ComplianceDisposition.NON_COMPLIANT : ComplianceDisposition.COMPLIANT;
    }

    private void persistReport(NcimsReport report) {
        try {
            String thresholdJson = objectMapper.writeValueAsString(Map.of(
                    "item16p_pasteurizationTemp", nullSafe(report.item16p_pasteurizationTemp()),
                    "item16p_holdTime", nullSafe(report.item16p_holdTime()),
                    "item7r_scc", nullSafe(report.item7r_scc()),
                    "item7p_spc", nullSafe(report.item7p_spc()),
                    "item7p_coliform", nullSafe(report.item7p_coliform()),
                    "item16p_phosphatase", nullSafe(report.item16p_phosphatase()),
                    "coolerTempCompliance", nullSafe(report.coolerTempCompliance())
            ));
            String passagesJson = objectMapper.writeValueAsString(report.pmoPassagesReferenced());

            jdbcTemplate.update("""
                    INSERT INTO gold_ncims_reports (
                        facility_name, plant_id, inspection_date, inspector_id,
                        regulatory_authority, threshold_results, overall_disposition,
                        llm_draft_narrative, pmo_passages_referenced,
                        audit_timestamp, model_used, prompt_version, batch_id
                    ) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, ?, ?, ?)
                    """,
                    report.facilityName(),
                    report.plantId(),
                    report.inspectionDate(),
                    report.inspectorId(),
                    report.regulatoryAuthority(),
                    thresholdJson,
                    report.overallDisposition().name(),
                    report.llmDraftNarrative(),
                    passagesJson,
                    Timestamp.from(report.auditTimestamp()),
                    report.modelUsed(),
                    report.promptVersion(),
                    report.batchId()
            );
        } catch (Exception e) {
            log.error("Failed to persist gold report for batchId={}", report.batchId(), e);
            throw new RuntimeException("Gold report persistence failed", e);
        }
    }

    private Object nullSafe(Object obj) {
        return obj != null ? obj : Map.of();
    }
}
