package com.example.docqa.web.dto;

public record IngestResponse(
        String filename,
        int chunksIndexed
) {
}
