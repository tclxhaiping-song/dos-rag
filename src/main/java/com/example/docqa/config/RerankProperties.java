package com.example.docqa.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 大模型 Rerank 配置。
 * <p>
 * 向量检索先召回较多候选片段，再由 Chat 模型按与问题的相关度重新排序，
 * 取 topK 作为最终结果。
 * </p>
 */
@Validated
@ConfigurationProperties(prefix = "app.rerank")
public class RerankProperties {

    /** 是否默认启用 LLM rerank（可被单次请求的 rerank 参数覆盖） */
    private boolean enabled = false;

    /** 候选倍数：实际召回数 = min(topK × candidateMultiplier, maxCandidates) */
    @Min(1)
    @Max(10)
    private int candidateMultiplier = 3;

    /** 单次 rerank 最多处理的候选片段数 */
    @Min(2)
    @Max(50)
    private int maxCandidates = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCandidateMultiplier() {
        return candidateMultiplier;
    }

    public void setCandidateMultiplier(int candidateMultiplier) {
        this.candidateMultiplier = candidateMultiplier;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }

    public int computeCandidateCount(int topK) {
        return Math.min(Math.max(topK, 1) * candidateMultiplier, maxCandidates);
    }
}
