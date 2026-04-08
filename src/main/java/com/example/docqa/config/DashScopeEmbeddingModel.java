package com.example.docqa.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 DashScope 原生 API 的 Embedding 模型实现。
 * <p>
 * 百炼平台部分模型（如 qwen3-vl-embedding）不支持 OpenAI 兼容模式（/compatible-mode），
 * 需要通过 DashScope 原生 REST API 调用。本类封装了该调用逻辑，
 * 实现 Spring AI 的 {@link EmbeddingModel} 接口，可无缝接入 MilvusVectorStore。
 * </p>
 *
 * <h3>支持的输入格式（InputFormat）</h3>
 * <ul>
 *   <li>{@code TEXTS} — 纯文本批量：{@code "input":{"texts":["t1","t2"]}}
 *       <br>端点：{@code /text-embedding/text-embedding}，支持批量</li>
 *   <li>{@code CONTENTS} — 多模态单条：{@code "input":{"contents":[{"text":"t1"}]}}
 *       <br>端点：{@code /multimodal-embedding/multimodal-embedding}，每次只能一条，内部自动循环</li>
 * </ul>
 */
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeEmbeddingModel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * DashScope 原生 API 的请求 input 格式。
     */
    public enum InputFormat {
        /**
         * 纯文本批量格式，适用于 text-embedding-v1/v2/v3。
         * <pre>
         * POST /api/v1/services/embeddings/text-embedding/text-embedding
         * {"model":"...", "input":{"texts":["t1","t2"]}, "parameters":{"dimension":1024}}
         * </pre>
         */
        TEXTS,

        /**
         * 多模态单条格式，适用于 qwen3-vl-embedding 等 VL 模型。
         * <p>每次 API 调用只能处理一条文本（contents 表示一个文档的内容片段，不是批量）。</p>
         * <pre>
         * POST /api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
         * {"model":"...", "input":{"contents":[{"text":"t1"}]}, "parameters":{"dimension":1024}}
         * </pre>
         */
        CONTENTS
    }

    private final RestClient restClient;
    private final String apiUrl;
    private final String model;
    private final int dimension;
    private final int batchSize;
    private final InputFormat inputFormat;

    /**
     * @param apiKey      百炼平台 API Key
     * @param apiUrl      DashScope 原生 API 端点 URL
     * @param model       模型名称
     * @param dimension   输出向量维度
     * @param batchSize   TEXTS 模式下每批最大文本数（CONTENTS 模式下忽略，固定每次 1 条）
     * @param inputFormat 请求格式
     */
    public DashScopeEmbeddingModel(String apiKey, String apiUrl, String model,
                                   int dimension, int batchSize, InputFormat inputFormat) {
        this.apiUrl = apiUrl;
        this.model = model;
        this.dimension = dimension;
        this.batchSize = batchSize;
        this.inputFormat = inputFormat;
        this.restClient = RestClient.builder()
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();

        log.info("DashScope Embedding 初始化完成 → model={}, dimension={}, format={}, batchSize={}, url={}",
                model, dimension, inputFormat, batchSize, apiUrl);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse resp = call(new EmbeddingRequest(List.of(text), null));
        return resp.getResult().getOutput();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }

        log.info("DashScope Embedding 开始: 共 {} 条文本, 格式={}", texts.size(), inputFormat);

        List<Embedding> allEmbeddings;
        if (inputFormat == InputFormat.CONTENTS) {
            // 多模态模式：每次只能处理一条文本，逐条调用
            allEmbeddings = embedOneByOne(texts);
        } else {
            // 纯文本模式：支持批量，按 batchSize 分批
            allEmbeddings = embedInBatches(texts);
        }

        log.info("DashScope Embedding 完成: {} 条文本 → {} 个向量", texts.size(), allEmbeddings.size());
        return new EmbeddingResponse(allEmbeddings);
    }

    // ==================== TEXTS 模式：批量调用 ====================

    private List<Embedding> embedInBatches(List<String> texts) {
        List<Embedding> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += batchSize) {
            int end = Math.min(i + batchSize, texts.size());
            List<String> batch = texts.subList(i, end);
            log.debug("TEXTS 批次 [{}-{}] / {}", i, end - 1, texts.size());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("input", Map.of("texts", batch));
            body.put("parameters", Map.of("dimension", dimension));

            Map<String, Object> response = callApi(body);
            result.addAll(parseEmbeddings(response, i));
        }
        return result;
    }

    // ==================== CONTENTS 模式：逐条调用 ====================

    private List<Embedding> embedOneByOne(List<String> texts) {
        List<Embedding> result = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            if (i > 0 && i % 10 == 0) {
                log.info("CONTENTS 进度: {}/{}", i, texts.size());
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            // contents 是一个文档的内容片段数组，每次只放一条文本
            body.put("input", Map.of("contents", List.of(Map.of("text", texts.get(i)))));
            body.put("parameters", Map.of("dimension", dimension));

            Map<String, Object> response = callApi(body);
            List<Embedding> embeddings = parseEmbeddings(response, i);
            result.addAll(embeddings);
        }
        return result;
    }

    // ==================== 公共方法 ====================

    /**
     * 调用 DashScope API 并返回响应 Map。
     * 如果调用失败或返回错误，打印详细请求体帮助排错。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> callApi(Map<String, Object> requestBody) {
        // 首次调用或出错时打印完整请求体
        String bodyJson = toJson(requestBody);
        log.debug("DashScope 请求: POST {} | body={}", apiUrl, bodyJson);

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(apiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.error("DashScope API 调用异常! url={}, body={}", apiUrl, bodyJson, e);
            throw new RuntimeException("DashScope Embedding API 调用失败: " + e.getMessage()
                    + "\n请求体: " + bodyJson, e);
        }

        if (response == null) {
            throw new RuntimeException("DashScope Embedding API 返回空响应");
        }

        // DashScope 错误响应检查
        if (response.containsKey("code")) {
            String code = String.valueOf(response.get("code"));
            String message = String.valueOf(response.get("message"));
            String requestId = String.valueOf(response.getOrDefault("request_id", "unknown"));
            log.error("DashScope API 错误! code={}, message={}, request_id={}, 请求体={}",
                    code, message, requestId, bodyJson);
            throw new RuntimeException(
                    "DashScope Embedding API 错误: [" + code + "] " + message
                            + " (request_id=" + requestId + ")"
                            + "\n请检查: 1)模型名是否正确 2)API端点是否匹配模型类型 3)输入格式是否正确"
                            + "\n请求体: " + bodyJson);
        }

        return response;
    }

    /**
     * 从 DashScope 响应中解析 Embedding 向量。
     * 兼容 text_index（文本模型）和 index（多模态模型）两种索引字段名。
     */
    @SuppressWarnings("unchecked")
    private List<Embedding> parseEmbeddings(Map<String, Object> response, int indexOffset) {
        Map<String, Object> output = (Map<String, Object>) response.get("output");
        if (output == null) {
            throw new RuntimeException("DashScope 响应缺少 output 字段, 完整响应: " + toJson(response));
        }

        List<Map<String, Object>> embeddings = (List<Map<String, Object>>) output.get("embeddings");
        if (embeddings == null || embeddings.isEmpty()) {
            throw new RuntimeException("DashScope 响应缺少 embeddings 字段, 完整响应: " + toJson(response));
        }

        List<Embedding> result = new ArrayList<>();
        for (Map<String, Object> emb : embeddings) {
            int textIndex = 0;
            if (emb.containsKey("text_index")) {
                textIndex = ((Number) emb.get("text_index")).intValue();
            } else if (emb.containsKey("index")) {
                textIndex = ((Number) emb.get("index")).intValue();
            }

            List<? extends Number> vector = (List<? extends Number>) emb.get("embedding");
            float[] floats = new float[vector.size()];
            for (int j = 0; j < vector.size(); j++) {
                floats[j] = vector.get(j).floatValue();
            }
            result.add(new Embedding(floats, indexOffset + textIndex));
        }
        return result;
    }

    private String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }

    @Override
    public int dimensions() {
        return dimension;
    }
}


