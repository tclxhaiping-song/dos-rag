package com.example.docqa.chunk;

import com.example.docqa.config.ChunkProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class TextChunker {

    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?。？！…])\\s+");

    private final ChunkProperties props;

    public TextChunker(ChunkProperties props) {
        this.props = props;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.strip();
        return switch (props.getStrategy()) {
            case CHAR -> chunkByChars(normalized, props.getCharChunkSize(), props.getCharOverlap());
            case SENTENCE -> chunkBySentences(normalized, props.getSentenceMaxChars());
        };
    }

    static List<String> chunkByChars(String text, int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            return List.of(text);
        }
        overlap = Math.min(Math.max(overlap, 0), chunkSize - 1);
        List<String> out = new ArrayList<>();
        int start = 0;
        int len = text.length();
        while (start < len) {
            int end = Math.min(start + chunkSize, len);
            out.add(text.substring(start, end).strip());
            if (end >= len) {
                break;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }
        return out.stream().filter(s -> !s.isBlank()).toList();
    }

    static List<String> chunkBySentences(String text, int maxCharsPerChunk) {
        String[] parts = SENTENCE_END.split(text);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : parts) {
            String s = part.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (current.isEmpty()) {
                current.append(s);
                continue;
            }
            if (current.length() + 1 + s.length() > maxCharsPerChunk) {
                out.add(current.toString());
                current = new StringBuilder(s);
            } else {
                current.append(' ').append(s);
            }
        }
        if (!current.isEmpty()) {
            out.add(current.toString());
        }
        if (out.isEmpty() && !text.isBlank()) {
            out.addAll(chunkByChars(text, maxCharsPerChunk, 0));
        }
        return out;
    }
}
