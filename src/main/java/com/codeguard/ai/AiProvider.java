package com.codeguard.ai;

/**
 * A single point of abstraction over "call an LLM with a system + user
 * prompt, get text back". OpenAI, Gemini, and Ollama all implement this the
 * same way, so CodeReviewService never needs to know which one is active.
 */
public interface AiProvider {

    /**
     * @param jsonMode when true, ask the underlying API for strict JSON
     * output. Providers must not force JSON mode when false, or free-text
     * prompts would come back as JSON instead of readable text.
     */
    String complete(String systemPrompt, String userPrompt, boolean jsonMode) throws Exception;
}
