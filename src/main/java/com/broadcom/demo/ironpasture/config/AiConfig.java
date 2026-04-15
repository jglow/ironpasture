package com.broadcom.demo.ironpasture.config;

import org.springframework.context.annotation.Configuration;

/**
 * Profile-aware AI configuration.
 *
 * Both the "local" and "tanzu" profiles use identical Spring AI abstractions
 * (ChatModel, EmbeddingModel, VectorStore). The concrete implementations are
 * selected by which Spring AI starter is on the classpath and which profile
 * is active:
 *
 * <ul>
 *   <li><b>"local" profile</b> — OllamaChatModel and OllamaEmbeddingModel are
 *       auto-configured by the {@code spring-ai-ollama-spring-boot-starter}.
 *       Point {@code spring.ai.ollama.base-url} at your local Ollama instance.</li>
 *
 *   <li><b>"tanzu" profile</b> — OpenAiChatModel and OpenAiEmbeddingModel are
 *       auto-configured by the {@code spring-ai-openai-spring-boot-starter}.
 *       The Tanzu Platform GenAI tile exposes an OpenAI-compatible API, so
 *       the standard OpenAI starter works out of the box once
 *       {@code spring.ai.openai.base-url} and {@code spring.ai.openai.api-key}
 *       are bound via service bindings.</li>
 *
 *   <li><b>PgVectorStore</b> is auto-configured in both profiles by the
 *       {@code spring-ai-pgvector-store-spring-boot-starter} using the
 *       DataSource already on the classpath.</li>
 * </ul>
 *
 * This class exists as a hook for any custom bean wiring needed beyond
 * auto-configuration (e.g., custom retry templates, prompt options, or
 * additional tool registrations).
 */
@Configuration
public class AiConfig {
    // Auto-configuration handles ChatModel, EmbeddingModel, and VectorStore.
    // Add custom beans here only when you need to override defaults.
}
