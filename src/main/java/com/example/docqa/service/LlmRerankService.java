package com.example.docqa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于大语言模型的检索结果重排序服务。
 * <p>
 * 将向量化检索召回的候选片段与问题一并发送给 Chat 模型，
 * 由模型对每个片段的相关度打分（0~10），再按分数降序返回 topK。
 * </p>
 */
@Service
public class LlmRerankService {

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[\\s*\\{.*}]\\s*", Pattern.DOTALL);

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public LlmRerankService(
            @Autowired(required = false) ChatModel chatModel,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    public boolean isAvailable() {
        return chatModel != null;
    }

    /**
     * 对候选文档片段按与问题的相关度重排序，返回 topK 条。
     * 若 Chat 模型不可用或解析失败，则回退为向量检索原始顺序并截断至 topK。
     */
    public List<Document> rerank(String question, List<Document> candidates, int topK) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        if (chatModel == null) {
            return truncate(candidates, topK);
        }

        int limit = Math.min(candidates.size(), topK);
        if (candidates.size() == 1) {
            return List.of(enrichWithRerankScore(candidates.getFirst(), 10.0, 1));
        }

        try {
            String response = callRerankModel(question, candidates);
            List<ScoredIndex> scores = parseScores(response, candidates.size());
            return applyScores(candidates, scores, limit);
        } catch (Exception e) {
            return truncate(candidates, limit);
        }
    }

    private String callRerankModel(String question, List<Document> candidates) {
        StringJoiner passages = new StringJoiner("\n\n");
        for (int i = 0; i < candidates.size(); i++) {
            passages.add("[" + (i + 1) + "] " + extractRerankText(candidates.get(i)));
        }

        String system = """
                你是文档检索重排序助手。给定用户问题和若干参考片段，请评估每个片段与问题的相关度。
                评分标准：10 分表示直接且完整地回答或支撑问题，0 分表示完全无关。
                仅依据片段内容判断，不要编造片段中不存在的信息。
                必须返回 JSON 数组，每个元素包含 index（片段编号，从 1 开始）和 score（0~10 的数字，可含小数）。
                不要输出任何 JSON 以外的文字。""";

        String user = "问题：\n" + question + "\n\n参考片段：\n" + passages;

        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system(system)
                .user(user)
                .call()
                .content();
    }

    /** 用于 rerank 评分的文本：父子索引模式下使用实际命中的子块文本 */
    private static String extractRerankText(Document doc) {
        String text = doc.getText();
        return text != null ? text.strip() : "";
    }

    private List<ScoredIndex> parseScores(String response, int candidateCount) throws Exception {
        String json = extractJsonArray(response);
        List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});

        List<ScoredIndex> scores = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            int index = toInt(item.get("index")) - 1;
            double score = toDouble(item.get("score"));
            if (index >= 0 && index < candidateCount) {
                scores.add(new ScoredIndex(index, score));
            }
        }
        if (scores.isEmpty()) {
            throw new IllegalArgumentException("empty rerank scores");
        }
        return scores;
    }

    private static String extractJsonArray(String response) {
        String trimmed = response.strip();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }
        Matcher matcher = JSON_ARRAY_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        throw new IllegalArgumentException("no JSON array in rerank response");
    }

    private List<Document> applyScores(List<Document> candidates, List<ScoredIndex> scores, int topK) {
        scores.sort(Comparator.comparingDouble(ScoredIndex::score).reversed());

        Set<Integer> used = new HashSet<>();
        List<Document> result = new ArrayList<>();
        int rank = 1;

        for (ScoredIndex scored : scores) {
            if (used.add(scored.index())) {
                result.add(enrichWithRerankScore(candidates.get(scored.index()), scored.score(), rank++));
            }
            if (result.size() >= topK) {
                return result;
            }
        }

        for (int i = 0; i < candidates.size() && result.size() < topK; i++) {
            if (!used.contains(i)) {
                result.add(enrichWithRerankScore(candidates.get(i), null, rank++));
            }
        }
        return result;
    }

    private static Document enrichWithRerankScore(Document doc, Double rerankScore, int rerankRank) {
        Map<String, Object> meta = new HashMap<>(doc.getMetadata() != null ? doc.getMetadata() : Map.of());
        if (rerankScore != null) {
            meta.put("rerankScore", rerankScore);
        }
        meta.put("rerankRank", rerankRank);
        return Document.builder()
                .id(doc.getId())
                .text(doc.getText())
                .metadata(meta)
                .build();
    }

    private static List<Document> truncate(List<Document> candidates, int topK) {
        return candidates.subList(0, Math.min(topK, candidates.size()));
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private record ScoredIndex(int index, double score) {}
}
