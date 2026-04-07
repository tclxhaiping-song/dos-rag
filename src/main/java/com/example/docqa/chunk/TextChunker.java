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

    /**
     * 将文档原始文本切分为适合向量化与检索的片段列表。
     * <p>
     * 切分策略由配置决定：
     * <ul>
     *   <li>CHAR：按固定字符窗口切分，并保留重叠内容，适合通用场景</li>
     *   <li>SENTENCE：优先按句子边界合并到最大长度，尽量避免截断语义</li>
     * </ul>
     * 当输入为空或仅空白字符时，返回空列表。
     * </p>
     *
     * @param text 从文件中提取出的原始全文文本
     * @return 切分后的片段列表（用于 embedding 入库和后续相似度检索）
     */
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

    /**
     * 按固定字符窗口切分文本，并在相邻片段之间保留重叠区。
     * <p>
     * 该方式速度快、实现简单，适合通用切分场景。
     * 例如 chunkSize=800、overlap=120 时，后一段会与前一段共享 120 个字符，
     * 能降低语义在边界处被截断的风险。
     * </p>
     *
     * @param text      原始文本
     * @param chunkSize 每个片段的最大字符数；小于等于 0 时直接返回整段文本
     * @param overlap   相邻片段重叠字符数（会自动约束到 0 ~ chunkSize-1）
     * @return 去除空白后的片段列表
     */
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

    /**
     * 按句子边界优先切分，并把多个句子合并到不超过最大长度的片段中。
     * <p>
     * 相比字符切分，该方式更能保持语义完整性，适合中文/英文自然段文本。
     * 若句子分割失败或结果为空，会回退到字符切分（无重叠）保证至少有可用片段。
     * </p>
     *
     * @param text             原始文本
     * @param maxCharsPerChunk 每个片段允许的最大字符数
     * @return 句子感知后的片段列表
     */
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
