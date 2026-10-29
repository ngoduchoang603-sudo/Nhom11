package com.shop.ai.adapter.out.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.ai.application.port.out.AssistantModel;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** OpenAI-compatible HTTP adapter. Configure AI_PROVIDER_URL and AI_API_KEY to enable it. */
@ApplicationScoped
public class OpenAiCompatibleModel implements AssistantModel {
    private static final Logger LOG = Logger.getLogger(OpenAiCompatibleModel.class);
    // Docker Desktop can prefer an unreachable IPv6 route for external providers.
    // Give the provider enough time after the IPv4 fallback is selected.
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Instant rateLimitedUntil = Instant.EPOCH;

    @ConfigProperty(name = "shop.ai.provider-url") Optional<String> providerUrl;
    @ConfigProperty(name = "shop.ai.api-key") Optional<String> apiKey;
    @ConfigProperty(name = "shop.ai.model", defaultValue = "gpt-4o-mini") String model;

    @Override public Optional<String> complete(String systemInstruction, String question) {
        String endpoint = providerUrl.orElse("");
        String key = apiKey.orElse("");
        if (endpoint.isBlank() || key.isBlank()) return Optional.empty();
        // Khi Gemini bao 429, khong tiep tuc goi den no trong mot luc. Application
        // se dung Agent fallback ngay, tranh de UI phai doi request timeout.
        if (Instant.now().isBefore(rateLimitedUntil)) return Optional.empty();
        try {
            var body = mapper.createObjectNode(); body.put("model", model);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemInstruction);
            messages.addObject().put("role", "user").put("content", question);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + key)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                if (response.statusCode() == 429) {
                    rateLimitedUntil = Instant.now().plus(Duration.ofMinutes(5));
                }
                LOG.warnf("OpenAI request failed with HTTP %d", response.statusCode());
                return Optional.empty();
            }
            JsonNode content = mapper.readTree(response.body()).at("/choices/0/message/content");
            return content.isTextual() && !content.asText().isBlank() ? Optional.of(content.asText()) : Optional.empty();
        } catch (Exception e) {
            LOG.warnf("OpenAI request failed: %s", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
