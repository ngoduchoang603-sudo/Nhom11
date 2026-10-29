package com.shop.agent.adapter.out.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The agent reads Product Service through REST; it never accesses its database. */
@ApplicationScoped
public class ProductCatalogTool {
    private static final Pattern MILLION_PRICE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:triệu|trieu|tr)\\b", Pattern.CASE_INSENSITIVE);
    /** Model numbers only: avoids treating RAM 12GB as a request for phone generation 12. */
    private static final Pattern MODEL_NUMBER = Pattern.compile("\\b(\\d{1,3})(?=\\s*(?:pro|max|plus|ultra|fe|e|t)\\b)|\\b(?:iphone|galaxy|samsung|xiaomi|oppo|vivo|realme)\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAM_REQUEST = Pattern.compile("\\bram\\s*(\\d{1,2})\\s*(?:gb)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of("tôi", "muốn", "biết", "thông", "tin", "chi", "tiết", "điện", "thoại", "cần", "cho", "với", "và", "của", "còn", "hàng", "giá", "sản", "phẩm", "này", "là", "theo", "nhu", "cầu");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    @ConfigProperty(name = "shop.product-service.url") String productServiceUrl;

    public List<ProductSummary> search(String question) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(productServiceUrl + "/api/products")).timeout(Duration.ofSeconds(8)).GET().build();
            JsonNode products = mapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            String[] keywords = question.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").split("\\s+");
            List<String> requestedModelNumbers = requestedModelNumbers(question);
            Integer requestedRam = requestedRam(question);
            Long requestedPrice = requestedPrice(question);
            boolean wantsMaximum = asksForMaximum(question);
            List<ScoredProduct> candidates = new ArrayList<>();
            for (JsonNode product : products) {
                String name = product.path("name").asText("");
                if (!requestedModelNumbers.isEmpty() && requestedModelNumbers.stream().noneMatch(number -> containsModelNumber(name, number))) {
                    continue;
                }
                String ram = product.path("ram").asText("");
                if (requestedRam != null && !containsGigabytes(ram, requestedRam)) continue;
                String description = product.path("description").asText("");
                String searchable = (name + " " + description + " " + ram + " " + product.path("storage").asText("")).toLowerCase();
                int score = java.util.Arrays.stream(keywords)
                        .filter(word -> word.length() > 1 && !STOP_WORDS.contains(word))
                        .mapToInt(word -> name.toLowerCase().contains(word) ? 3 : (searchable.contains(word) ? 1 : 0))
                        .sum();
                if (requestedRam != null) score += 3;
                long price = product.path("price").asLong();
                boolean matchesPrice = requestedPrice != null && (wantsMaximum ? price <= requestedPrice : price >= requestedPrice);
                if ((score > 0 || matchesPrice) && (requestedPrice == null || matchesPrice)) {
                    candidates.add(new ScoredProduct(score, new ProductSummary(product.path("id").asText(), name, product.path("category").asText(""), price, product.path("stock").asLong(),
                            product.path("ram").asText(""), product.path("storage").asText(""), product.path("screen").asText(""),
                            product.path("battery").asText(""), product.path("image").asText(""), product.path("availableColors").asText(""))));
                }
            }
            candidates.sort(Comparator.comparingInt(ScoredProduct::score).reversed());
            List<ProductSummary> matches = candidates.stream().limit(5).map(ScoredProduct::product).toList();
            return matches;
        } catch (Exception ignored) { return List.of(); }
    }

    /** A deterministic recommendation ranker. It remains explainable and does not invent stock or price. */
    public List<ProductSummary> recommend(String latestSearch, List<String> recentProductIds) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(productServiceUrl + "/api/products")).timeout(Duration.ofSeconds(8)).GET().build();
            JsonNode products = mapper.readTree(http.send(request, HttpResponse.BodyHandlers.ofString()).body());
            String query = latestSearch == null ? "" : latestSearch.toLowerCase();
            List<ScoredProduct> ranked = new ArrayList<>();
            for (JsonNode product : products) {
                String id = product.path("id").asText();
                String name = product.path("name").asText("");
                String category = product.path("category").asText("");
                String searchable = (name + " " + category + " " + product.path("description").asText("")).toLowerCase();
                int score = recentProductIds.contains(id) ? 90 : 55;
                for (String word : query.replaceAll("[^\\p{L}\\p{N}]+", " ").split("\\s+")) {
                    if (word.length() > 2 && searchable.contains(word)) score += 12;
                }
                ranked.add(new ScoredProduct(score, new ProductSummary(id, name, category, product.path("price").asLong(), product.path("stock").asLong(),
                        product.path("ram").asText(""), product.path("storage").asText(""), product.path("screen").asText(""), product.path("battery").asText(""),
                        product.path("image").asText(""), product.path("availableColors").asText(""))));
            }
            ranked.sort(Comparator.comparingInt(ScoredProduct::score).reversed());
            return ranked.stream().filter(p -> p.product().stock() > 0).limit(3).map(ScoredProduct::product).toList();
        } catch (Exception ignored) { return List.of(); }
    }

    public ProductSummary findById(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            JsonNode p = mapper.readTree(http.send(HttpRequest.newBuilder(URI.create(productServiceUrl + "/api/products/" + id)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString()).body());
            return new ProductSummary(p.path("id").asText(), p.path("name").asText(), p.path("category").asText(""), p.path("price").asLong(), p.path("stock").asLong(), p.path("ram").asText(""), p.path("storage").asText(""), p.path("screen").asText(""), p.path("battery").asText(""), p.path("image").asText(""), p.path("availableColors").asText(""));
        } catch (Exception ignored) { return null; }
    }

    /** Re-plan helper: returns live in-stock alternatives, never silently swaps a cart item. */
    public List<ProductSummary> alternatives(String question, String excludedId) {
        List<ProductSummary> ranked = recommend(question, List.of());
        return ranked.stream().filter(p -> !p.id().equals(excludedId)).limit(3).toList();
    }

    /** Nhan cac cach viet nhu "15 trieu", "15 triệu" de loc gia toi thieu. */
    private boolean asksForMaximum(String question) {
        String text = question == null ? "" : question.toLowerCase();
        return text.contains("dưới") || text.contains("duoi") || text.contains("nhỏ hơn") || text.contains("thấp hơn") || text.contains("<=");
    }

    private Long requestedPrice(String question) {
        if (question == null) return null;
        Matcher matcher = MILLION_PRICE.matcher(question.toLowerCase());
        if (!matcher.find()) return null;
        try {
            return Math.round(Double.parseDouble(matcher.group(1).replace(',', '.')) * 1_000_000L);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> requestedModelNumbers(String question) {
        if (question == null) return List.of();
        Matcher matcher = MODEL_NUMBER.matcher(question.toLowerCase());
        List<String> numbers = new ArrayList<>();
        while (matcher.find()) {
            String number = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (number != null && !numbers.contains(number)) numbers.add(number);
        }
        return numbers;
    }

    private boolean containsModelNumber(String productName, String number) {
        return Pattern.compile("(?<!\\d)" + Pattern.quote(number) + "(?!\\d)").matcher(productName).find();
    }

    private Integer requestedRam(String question) {
        if (question == null) return null;
        Matcher matcher = RAM_REQUEST.matcher(question.toLowerCase());
        if (!matcher.find()) return null;
        try { return Integer.valueOf(matcher.group(1)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private boolean containsGigabytes(String value, int gigabytes) {
        return Pattern.compile("(?<!\\d)" + gigabytes + "\\s*gb\\b", Pattern.CASE_INSENSITIVE).matcher(value == null ? "" : value).find();
    }
    private record ScoredProduct(int score, ProductSummary product) { }
    public record ProductSummary(String id, String name, String category, long price, long stock, String ram, String storage, String screen, String battery, String image, String availableColors) { }
}
