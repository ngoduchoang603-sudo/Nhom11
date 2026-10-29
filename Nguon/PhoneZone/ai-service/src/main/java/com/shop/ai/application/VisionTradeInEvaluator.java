package com.shop.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/** Validates trade-in photos with the configured multimodal provider before pricing. */
@ApplicationScoped
public class VisionTradeInEvaluator {
    private static final Logger LOG = Logger.getLogger(VisionTradeInEvaluator.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @ConfigProperty(name = "shop.ai.provider-url") Optional<String> providerUrl;
    @ConfigProperty(name = "shop.ai.api-key") Optional<String> apiKey;
    @ConfigProperty(name = "shop.ai.model", defaultValue = "gpt-4o-mini") String model;

    public Evaluation evaluate(String deviceName, Map<String, String> images, long referencePrice) {
        if (providerUrl.orElse("").isBlank() || apiKey.orElse("").isBlank()) return unavailable("Chưa cấu hình Vision AI nên chưa thể xác minh ảnh.");
        try {
            JsonNode vision = requestVision(deviceName, images);
            boolean canEstimate = vision.path("canEstimate").asBoolean(false);
            double confidence = vision.path("confidence").asDouble(0d);
            String grade = vision.path("overall_grade").asText("UNABLE_TO_ASSESS").toUpperCase();
            if (!canEstimate || confidence < .55d || !List.of("A", "B", "C", "D").contains(grade)) {
                return rejected(vision, invalidImageMessage(vision));
            }
            double[] factor = switch (grade) { case "A" -> new double[]{.72d, .82d}; case "B" -> new double[]{.60d, .70d}; case "C" -> new double[]{.45d, .55d}; default -> new double[]{.25d, .40d}; };
            Map<String, Object> result = copy(vision);
            result.put("canEstimate", true);
            int conditionPercent = switch (grade) { case "A" -> 92; case "B" -> 76; case "C" -> 58; default -> 38; };
            result.put("condition_percent", conditionPercent);
            result.put("price_factor_min", factor[0]);
            result.put("price_factor_max", factor[1]);
            result.put("disclaimer", "Giá AI chỉ là ước tính tham khảo; cửa hàng kiểm tra máy thực tế để chốt giá cuối.");
            return new Evaluation(result, Math.round(referencePrice * factor[0]), Math.round(referencePrice * factor[1]));
        } catch (Exception e) {
            LOG.warnf("Vision evaluation unavailable: %s", e.getMessage());
            return unavailable("AI chưa đọc được ảnh này nên chưa thể đưa giá. Hãy chụp lại đủ 6 ảnh rõ nét hoặc mang máy đến cửa hàng.");
        }
    }

    private JsonNode requestVision(String deviceName, Map<String, String> images) throws Exception {
        var body = mapper.createObjectNode(); body.put("model", model); body.put("temperature", 0);
        var messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", "You are a strict phone trade-in photo validator. Return ONLY JSON: {screen,back,frame,overall_grade,confidence,damage_signals,invalid_views,canEstimate}. screen, back and frame must be concise Vietnamese condition descriptions. damage_signals is a concise Vietnamese array of visible defects only. invalid_views is an array of invalid labels from front, back, left, right, top, bottom. Analyse supplied images only. canEstimate must be false for non-phone, unrelated, duplicate, blurred, missing views, or uncertainty. Never infer a grade or price when uncertain. overall_grade is A, B, C, D, or UNABLE_TO_ASSESS.");
        var content = messages.addObject().put("role", "user").putArray("content");
        content.addObject().put("type", "text").put("text", "Device declared: " + deviceName + ". Validate views front, back, left, right, top, bottom.");
        for (String view : List.of("front", "back", "left", "right", "top", "bottom")) {
            content.addObject().put("type", "text").put("text", "View: " + view);
            content.addObject().put("type", "image_url").putObject("image_url").put("url", images.get(view));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(providerUrl.orElseThrow())).timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey.orElseThrow())
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("Vision provider HTTP " + response.statusCode());
        String raw = mapper.readTree(response.body()).at("/choices/0/message/content").asText().trim();
        raw = raw.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "").trim();
        JsonNode parsed = mapper.readTree(raw);
        if (!parsed.isObject()) throw new IllegalArgumentException("Vision response is not JSON");
        return parsed;
    }

    private Evaluation unavailable(String message) { return new Evaluation(Map.of("canEstimate", false, "status", "VISION_UNAVAILABLE", "disclaimer", message), 0, 0); }
    private Evaluation rejected(JsonNode vision, String message) { Map<String, Object> result = copy(vision); result.put("canEstimate", false); result.put("status", "IMAGE_REJECTED"); result.put("disclaimer", message); return new Evaluation(result, 0, 0); }
    private String invalidImageMessage(JsonNode vision) {
        List<String> invalid = new ArrayList<>();
        vision.path("invalid_views").forEach(view -> {
            String label = switch (view.asText().toLowerCase()) {
                case "front" -> "mặt trước"; case "back" -> "mặt sau"; case "left" -> "cạnh trái";
                case "right" -> "cạnh phải"; case "top" -> "cạnh trên"; case "bottom" -> "cạnh dưới"; default -> "";
            };
            if (!label.isBlank()) invalid.add(label);
        });
        if (!invalid.isEmpty() && invalid.size() < 6) return "Ảnh " + String.join(", ", invalid) + " bị mờ hoặc không hợp lệ. Hãy chụp lại rõ nét đúng góc này để AI báo giá.";
        return "Bộ ảnh chưa hợp lệ. Hãy chụp lại rõ nét, đúng một chiếc điện thoại và đủ 6 góc: mặt trước, mặt sau, cạnh trái/phải, cạnh trên/dưới.";
    }
    private Map<String, Object> copy(JsonNode node) { return mapper.convertValue(node, LinkedHashMap.class); }
    public record Evaluation(Map<String, Object> result, long min, long max) { }
}
