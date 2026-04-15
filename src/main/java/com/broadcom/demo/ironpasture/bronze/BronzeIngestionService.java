package com.broadcom.demo.ironpasture.bronze;

import com.broadcom.demo.ironpasture.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class BronzeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BronzeIngestionService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VectorStore vectorStore;

    public BronzeIngestionService(JdbcTemplate jdbcTemplate, VectorStore vectorStore) {
        this.jdbcTemplate = jdbcTemplate;
        this.vectorStore = vectorStore;
    }

    /**
     * Validates structure of a sensor reading and persists it to the bronze_sensor_readings table.
     *
     * @return the generated bronze ID
     */
    public long ingestSensorReading(SensorReading reading) {
        Objects.requireNonNull(reading.plantId(), "plantId must not be null");
        Objects.requireNonNull(reading.batchId(), "batchId must not be null");
        Objects.requireNonNull(reading.operatorId(), "operatorId must not be null");
        Objects.requireNonNull(reading.readingTimestamp(), "readingTimestamp must not be null");

        String sql = """
                INSERT INTO bronze_sensor_readings (
                    plant_id, reading_timestamp, pasteurization_temp_f, htst_hold_time_seconds,
                    raw_milk_somatic_cell_count, processed_milk_spc, coliform_count,
                    ph, cooler_temp_f, phosphatase_test, operator_id, batch_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, reading.plantId());
            ps.setTimestamp(2, Timestamp.from(reading.readingTimestamp()));
            ps.setDouble(3, reading.pasteurizationTempF());
            ps.setDouble(4, reading.htstHoldTimeSeconds());
            ps.setInt(5, reading.rawMilkSomaticCellCount());
            ps.setInt(6, reading.processedMilkSpc());
            ps.setInt(7, reading.coliformCount());
            ps.setDouble(8, reading.pH());
            ps.setDouble(9, reading.coolerTempF());
            ps.setString(10, reading.phosphataseTest());
            ps.setString(11, reading.operatorId());
            ps.setString(12, reading.batchId());
            return ps;
        }, keyHolder);

        long bronzeId = keyHolder.getKey().longValue();
        log.info("Ingested sensor reading to bronze layer: bronzeId={}, batchId={}", bronzeId, reading.batchId());
        return bronzeId;
    }

    /**
     * Reads pmo_chunks.csv from classpath, persists rows to bronze_pmo_chunks,
     * and creates Document objects with metadata for the VectorStore.
     */
    public int ingestPmoChunks() {
        var resource = new ClassPathResource("pmo_chunks.csv");
        List<Document> documents = new ArrayList<>();

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("pmo_chunks.csv is empty");
                return 0;
            }

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 3) continue;

                String section = parts[0].trim();
                String itemCode = parts[1].trim();
                String text = parts[2].trim();

                jdbcTemplate.update("""
                        INSERT INTO bronze_pmo_chunks (section, item_code, chunk_text)
                        VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """, section, itemCode, text);

                var doc = new Document(text, Map.of(
                        "section", section,
                        "itemCode", itemCode,
                        "source", "PMO"
                ));
                documents.add(doc);
                count++;
            }

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                log.info("Indexed {} PMO chunks into VectorStore", documents.size());
            }

            return count;
        } catch (Exception e) {
            log.error("Failed to ingest pmo_chunks.csv", e);
            throw new RuntimeException("PMO chunk ingestion failed", e);
        }
    }

    private String[] parseCsvLine(String line) {
        // Simple CSV parse handling quoted fields
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }
}
