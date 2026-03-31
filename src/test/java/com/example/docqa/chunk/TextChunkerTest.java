package com.example.docqa.chunk;

import com.example.docqa.config.ChunkProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {

    @Test
    void chunkByChars_overlap() {
        List<String> parts = TextChunker.chunkByChars("abcdefghijklmnop", 6, 2);
        assertThat(parts).hasSizeGreaterThanOrEqualTo(2);
        assertThat(parts.getFirst()).hasSize(6);
    }

    @Test
    void chunkBySentences_mergesUnderMax() {
        ChunkProperties p = new ChunkProperties();
        p.setStrategy(ChunkProperties.Strategy.SENTENCE);
        p.setSentenceMaxChars(80);
        TextChunker chunker = new TextChunker(p);
        String s = "第一句。第二句！第三句？继续一句。";
        List<String> parts = chunker.chunk(s);
        assertThat(parts).isNotEmpty();
        assertThat(String.join("", parts)).contains("第一句");
    }
}
