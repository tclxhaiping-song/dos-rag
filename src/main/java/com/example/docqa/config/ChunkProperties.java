package com.example.docqa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 文档分块配置属性。
 * <p>
 * 支持三种策略：
 * <ul>
 *   <li>CHAR - 按固定字符窗口切分，并保留重叠区</li>
 *   <li>SENTENCE - 按句子边界合并到最大长度</li>
 *   <li>PARENT_CHILD - 父子索引：先切大父块(~1000字)提供上下文，再切小子块(~100字)用于精准检索</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.chunk")
public class ChunkProperties {

    public enum Strategy {
        /** 按固定字符窗口 + 重叠切分 */
        CHAR,
        /** 按句子边界合并切分 */
        SENTENCE,
        /** 父子索引：大父块提供上下文，小子块保证检索精准度 */
        PARENT_CHILD
    }

    @NotNull
    private Strategy strategy = Strategy.CHAR;

    // ========== CHAR 策略参数 ==========

    @Min(200)
    @Max(8000)
    private int charChunkSize = 800;

    @Min(0)
    @Max(2000)
    private int charOverlap = 120;

    // ========== SENTENCE 策略参数 ==========

    @Min(200)
    @Max(12000)
    private int sentenceMaxChars = 900;

    // ========== PARENT_CHILD 策略参数 ==========

    /** 父块大小（字符数），提供给大模型的上下文窗口，默认 1000 */
    @Min(200)
    @Max(12000)
    private int parentChunkSize = 1000;

    /** 父块之间的重叠字符数，防止语义在父块边界截断，默认 200 */
    @Min(0)
    @Max(3000)
    private int parentOverlap = 200;

    /** 子块大小（字符数），用于向量化和精准检索，默认 100 */
    @Min(20)
    @Max(2000)
    private int childChunkSize = 100;

    /** 子块之间的重叠字符数，默认 20 */
    @Min(0)
    @Max(500)
    private int childOverlap = 20;

    // ========== Getters & Setters ==========

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public int getCharChunkSize() {
        return charChunkSize;
    }

    public void setCharChunkSize(int charChunkSize) {
        this.charChunkSize = charChunkSize;
    }

    public int getCharOverlap() {
        return charOverlap;
    }

    public void setCharOverlap(int charOverlap) {
        this.charOverlap = charOverlap;
    }

    public int getSentenceMaxChars() {
        return sentenceMaxChars;
    }

    public void setSentenceMaxChars(int sentenceMaxChars) {
        this.sentenceMaxChars = sentenceMaxChars;
    }

    public int getParentChunkSize() {
        return parentChunkSize;
    }

    public void setParentChunkSize(int parentChunkSize) {
        this.parentChunkSize = parentChunkSize;
    }

    public int getParentOverlap() {
        return parentOverlap;
    }

    public void setParentOverlap(int parentOverlap) {
        this.parentOverlap = parentOverlap;
    }

    public int getChildChunkSize() {
        return childChunkSize;
    }

    public void setChildChunkSize(int childChunkSize) {
        this.childChunkSize = childChunkSize;
    }

    public int getChildOverlap() {
        return childOverlap;
    }

    public void setChildOverlap(int childOverlap) {
        this.childOverlap = childOverlap;
    }
}
