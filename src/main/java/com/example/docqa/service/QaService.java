package com.example.docqa.service;

import com.example.docqa.web.dto.QaQueryRequest;
import com.example.docqa.web.dto.QaQueryResponse;
import com.example.docqa.web.dto.RetrievedChunk;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusSearchRequest;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

/**
 * 文档问答核心服务。
 * <p>
 * 实现 RAG（检索增强生成）流程：
 * 1. 将用户问题通过 Embedding 模型转为向量
 * 2. 在 Milvus 中进行相似度检索，找到最相关的文档片段
 * 3. 可选地将片段作为上下文，调用 Chat 模型生成自然语言回答
 * </p>
 */
@Service
public class QaService {

    /** 通用向量存储接口，用于执行相似度检索 */
    private final VectorStore vectorStore;
    /** Milvus 专用实例（如果底层确实是 Milvus），支持设置 nprobe 等高级搜索参数 */
    private final MilvusVectorStore milvusVectorStore;
    /** Chat 大语言模型，用于根据检索片段生成回答（可选注入，未配置时为 null） */
    private final ChatModel chatModel;

    public QaService(
            VectorStore vectorStore,
            @Autowired(required = false) ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.milvusVectorStore = vectorStore instanceof MilvusVectorStore m ? m : null;
        this.chatModel = chatModel;
    }

    /**
     * 执行文档问答查询。
     * <p>
     * 处理流程：
     * 1. 从请求中获取 topK（默认 5）和 similarityThreshold
     * 2. 调用 {@link #search} 从 Milvus 检索最相关的文档片段
     * 3. 将检索到的 Document 转换为 RetrievedChunk DTO
     * 4. 如果 generateAnswer=true 且 ChatModel 可用且有检索结果，调用 {@link #generateAnswer} 生成回答
     * </p>
     *
     * @param req 查询请求，包含问题、topK、相似度阈值、是否生成回答等参数
     * @return 查询响应，包含检索到的片段列表和可选的 AI 生成回答
     */
    public QaQueryResponse query(QaQueryRequest req) {
        int topK = req.topK() != null ? req.topK() : 5;
        List<Document> docs = search(req.question(), topK, req.similarityThreshold());

        List<RetrievedChunk> chunks = docs.stream()
                .map(d -> {
                    String t = d.getText();
                    return new RetrievedChunk(
                            d.getId(),
                            t != null ? t : "",
                            d.getMetadata() != null ? d.getMetadata() : java.util.Map.of());
                })
                .toList();

        String answer = null;
        if (Boolean.TRUE.equals(req.generateAnswer()) && chatModel != null && !chunks.isEmpty()) {
            answer = generateAnswer(req.question(), chunks);
        }

        return new QaQueryResponse(answer, chunks);
    }

    /**
     * 从 Milvus 向量库中检索与问题最相关的文档片段。
     * <p>
     * 内部会将 question 文本通过 Embedding 模型（text-embedding-v3）转为 1024 维向量，
     * 然后在 Milvus 中执行 ANN（近似最近邻）搜索。
     * 如果底层是 MilvusVectorStore，会额外设置 nprobe=32 以提高召回率。
     * </p>
     *
     * @param question            用户问题文本
     * @param topK                返回最相似的前 K 个片段
     * @param similarityThreshold 相似度阈值，为 null 时返回所有匹配结果
     * @return 检索到的文档片段列表，按相似度从高到低排序
     */
    private List<Document> search(String question, int topK, Double similarityThreshold) {
        var builder = SearchRequest.builder().query(question).topK(topK);
        if (similarityThreshold != null) {
            builder.similarityThreshold(similarityThreshold);
        }
        SearchRequest searchRequest = builder.build();

        if (milvusVectorStore != null) {
            var mb = MilvusSearchRequest.milvusBuilder()
                    .query(question)
                    .topK(topK)
                    .searchParamsJson("{\"nprobe\":32}");
            if (similarityThreshold != null) {
                mb.similarityThreshold(similarityThreshold);
            } else {
                mb.similarityThresholdAll();
            }
            return milvusVectorStore.similaritySearch(mb.build());
        }
        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 调用 Chat 大语言模型（qwen-plus）生成自然语言回答。
     * <p>
     * 将检索到的文档片段拼接为上下文，配合系统提示词一起发送给 Chat 模型。
     * 系统提示词要求模型仅基于提供的片段回答，不编造信息。
     * </p>
     *
     * @param question 用户的原始问题
     * @param chunks   检索到的相关文档片段，作为 Chat 模型的参考上下文
     * @return 模型生成的自然语言回答
     */
    private String generateAnswer(String question, List<RetrievedChunk> chunks) {
        StringJoiner ctx = new StringJoiner("\n---\n");
        int i = 1;
        for (RetrievedChunk c : chunks) {
            ctx.add("[" + i++ + "] " + c.text());
        }
        String system = "你是文档问答助手。请仅根据用户提供的参考片段回答问题；若片段不足以回答，请明确说明无法从文档中得出答案，不要编造。";
        String user = "参考片段：\n" + ctx + "\n\n问题：" + question;

        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system(system)
                .user(user)
                .call()
                .content();
    }
}
