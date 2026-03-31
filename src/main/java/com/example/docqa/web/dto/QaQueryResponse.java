package com.example.docqa.web.dto;

import java.util.List;

public record QaQueryResponse(
        String answer,
        List<RetrievedChunk> chunks
) {
}
