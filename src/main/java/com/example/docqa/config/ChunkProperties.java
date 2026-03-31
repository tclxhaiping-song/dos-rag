package com.example.docqa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "app.chunk")
public class ChunkProperties {

    public enum Strategy {
        CHAR,
        SENTENCE
    }

    @NotNull
    private Strategy strategy = Strategy.CHAR;

    @Min(200)
    @Max(8000)
    private int charChunkSize = 800;

    @Min(0)
    @Max(2000)
    private int charOverlap = 120;

    @Min(200)
    @Max(12000)
    private int sentenceMaxChars = 900;

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
}
