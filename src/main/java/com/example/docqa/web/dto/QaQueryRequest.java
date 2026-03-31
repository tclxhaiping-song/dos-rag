package com.example.docqa.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QaQueryRequest(
        @NotBlank @Size(max = 4000) String question,
        Integer topK,
        Double similarityThreshold,
        Boolean generateAnswer
) {
}
