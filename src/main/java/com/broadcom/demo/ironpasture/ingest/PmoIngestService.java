package com.broadcom.demo.ironpasture.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PmoIngestService {

    private static final Logger log = LoggerFactory.getLogger(PmoIngestService.class);

    private final VectorStore vectorStore;

    public PmoIngestService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Automatically loads pmo_chunks.csv into the VectorStore at startup on the "local" profile.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Profile("local")
    public void onStartupLocal() {
        try {
            log.info("Local profile detected — auto-ingesting PMO chunks at startup");
            reingest();
        } catch (Exception e) {
            log.warn("PMO auto-ingest failed at startup (Ollama may not be running): {}", e.getMessage());
        }
    }

    /**
     * Manually re-ingests pmo_chunks.csv into the VectorStore.
     * Can be triggered from the admin controller endpoint.
     *
     * @return the number of chunks ingested
     */
    public int reingest() {
        var resource = new ClassPathResource("seed/pmo_chunks.csv");
        List<Document> documents = new ArrayList<>();

        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            // Skip header line
            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("pmo_chunks.csv is empty");
                return 0;
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = parseCsvLine(line);
                if (parts.length < 4) {
                    log.warn("Skipping malformed CSV line: {}", line);
                    continue;
                }

                String chunkId = parts[0].trim();
                String section = parts[1].trim();
                String itemCode = parts[2].trim();
                String text = parts[3].trim();

                var doc = new Document(text, Map.of(
                        "chunkId", chunkId,
                        "section", section,
                        "itemCode", itemCode,
                        "source", "PMO"
                ));
                documents.add(doc);
            }

            if (!documents.isEmpty()) {
                vectorStore.add(documents);
                log.info("Ingested {} PMO chunks into VectorStore", documents.size());
            } else {
                log.warn("No PMO chunks found in pmo_chunks.csv");
            }

            return documents.size();

        } catch (Exception e) {
            log.error("Failed to ingest PMO chunks from pmo_chunks.csv", e);
            throw new RuntimeException("PMO chunk ingestion failed", e);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        var current = new StringBuilder();
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
