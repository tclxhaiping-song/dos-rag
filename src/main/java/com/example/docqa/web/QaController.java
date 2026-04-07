package com.example.docqa.web;

import com.example.docqa.service.QaService;
import com.example.docqa.web.dto.QaQueryRequest;
import com.example.docqa.web.dto.QaQueryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档问答控制器。
 * <p>
 * 提供基于 RAG（检索增强生成）的文档问答接口：
 * 1. 根据用户问题，从 Milvus 向量库中检索最相关的文档片段（chunks）
 * 2. 可选地将检索到的片段作为上下文，调用大语言模型生成自然语言回答
 * </p>
 *
 * <b>接口地址：</b> POST /api/qa/query
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    /**
     * 文档问答查询接口。
     * <p>
     * 接收用户的问题，从向量库中检索相关文档片段，并可选地生成 AI 回答。
     * </p>
     *
     * <b>请求示例：</b>
     * <pre>{@code
     * POST /api/qa/query
     * {
     *   "question": "什么是 Spring AI？",
     *   "topK": 5,
     *   "similarityThreshold": 0.7,
     *   "generateAnswer": true
     * }
     * }</pre>
     *
     * @param request 查询请求体，包含以下字段：
     *                <ul>
     *                  <li><b>question</b>（必填）- 用户提出的问题文本，最长 4000 字符</li>
     *                  <li><b>topK</b>（可选，默认 5）- 从向量库中检索最相似的前 K 个文档片段</li>
     *                  <li><b>similarityThreshold</b>（可选）- 相似度阈值（0~1），低于此阈值的片段会被过滤掉</li>
     *                  <li><b>generateAnswer</b>（可选，默认 false）- 是否调用大语言模型（qwen-plus）基于检索片段生成回答</li>
     *                </ul>
     * @return 查询响应，包含以下字段：
     *         <ul>
     *           <li><b>answer</b> - AI 生成的回答（仅当 generateAnswer=true 时有值，否则为 null）</li>
     *           <li><b>chunks</b> - 检索到的相关文档片段列表，每个片段包含 id、text、metadata</li>
     *         </ul>
     */
    @PostMapping("/query")
    public QaQueryResponse query(@Valid @RequestBody QaQueryRequest request) {
        return qaService.query(request);
    }
}
