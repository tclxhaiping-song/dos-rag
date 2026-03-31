package com.example.docqa.web.dto;

import java.util.Map;

public record RetrievedChunk(
        String id,
        String text,
        Map<String, Object> metadata
) {
}
