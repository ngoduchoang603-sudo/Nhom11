package com.shop.ai.application.port.out;

import java.util.Optional;

/** Port for an external LLM provider; keeps provider SDKs out of the use case. */
public interface AssistantModel {
    Optional<String> complete(String systemInstruction, String question);
}
