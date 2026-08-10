package com.codeguard.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bean name "gemini". Google renames/retires Gemini model names often - the
 * model comes ENTIRELY from gemini.model in application.properties, no
 * hardcoded fallback, so a rename never requires a code change.
 */
@Component("gemini")
public class GeminiProvider implements AiProvider {

    private static final String URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final String apiKey;
    private final String model;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiProvider(@Value("${gemini.api.key:}") String apiKey,
                           @Value("${gemini.model:}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("gemini.api.key is not set in application.properties.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("gemini.model is not set in application.properties.");
        }

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", 0.2);
        generationConfig.put("thinkingConfig", Map.of("thinkingLevel", "low"));
        generationConfig.put("maxOutputTokens", 4000);
        if (jsonMode) {
            generationConfig.put("response_mime_type", "application/json");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        body.put("contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))));
        body.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = String.format(URL_TEMPLATE, model, apiKey);
        String raw;
        try {
            raw = restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException(
                    "Gemini API error (" + ex.getStatusCode() + "): " + ex.getResponseBodyAsString(), ex);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Couldn't reach Gemini's API. Check your network connection.", ex);
        }

        JsonNode root = objectMapper.readTree(raw);
        JsonNode candidate = root.path("candidates").get(0);

        if ("MAX_TOKENS".equals(candidate.path("finishReason").asText(""))) {
            throw new IllegalStateException(
                    "Gemini's reply was cut off before finishing. Large diffs may need a higher maxOutputTokens "
                            + "in GeminiProvider.java, or splitting the review into smaller batches.");
        }

        return candidate.path("content").path("parts").get(0).path("text").asText();
    }
}
