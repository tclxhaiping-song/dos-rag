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

import java.util.*;

/**
 * 文档问答核心服务。
 * <p>
 * 实现 RAG（检索增强生成）流程：
 * <ol>
 *   <li>将用户问题通过 Embedding 模型（text-embedding-v3）转为 1024 维向量</li>
 *   <li>在 Milvus 中进行相似度检索，找到最相关的文档片段</li>
 *   <li>可选地将片段作为上下文，调用 Chat 模型（qwen-plus）生成自然语言回答</li>
 * </ol>
 * <p>
 * 支持两种检索模式（自动根据检索到的数据判断，无需依赖配置）：
 * <ul>
 *   <li>传统模式（CHAR / SENTENCE）：检索到的片段直接作为上下文</li>
 *   <li>父子索引模式（PARENT_CHILD）：用小子块精准检索，用大父块喂给大模型提供完整上下文；
 *       响应中 text 展示父块文本，metadata.childText 保留实际命中的子块文本</li>
 * </ul>
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
     * <ol>
     *   <li>从请求中获取 topK（默认 5）和 similarityThreshold</li>
     *   <li>调用 {@link #search} 从 Milvus 检索最相关的文档片段（子块）</li>
     *   <li>自动检测检索到的数据是否包含 parentText（数据驱动，兼容混合数据）</li>
     *   <li>如果是父子索引数据：响应的 text 展示父块文本，metadata.childText 保留命中的子块文本</li>
     *   <li>如果是传统数据：响应的 text 直接展示匹配到的片段文本</li>
     *   <li>如果 generateAnswer=true 且 ChatModel 可用且有检索结果，调用 {@link #generateAnswer} 生成回答</li>
     * </ol>
     *
     * @param req 查询请求，包含问题、topK、相似度阈值、是否生成回答等参数
     * @return 查询响应，包含检索到的片段列表和可选的 AI 生成回答
     */
    public QaQueryResponse query(QaQueryRequest req) {
        int topK = req.topK() != null ? req.topK() : 5;
        List<Document> docs = search(req.question(), topK, req.similarityThreshold());

        // 判断检索到的数据是否来自父子索引模式（基于实际数据而非配置，兼容混合数据）
        boolean hasParentChild = docs.stream()
                .anyMatch(d -> d.getMetadata() != null
                        && d.getMetadata().get("parentText") instanceof String pt
                        && !pt.isBlank());

        List<RetrievedChunk> chunks = docs.stream()
                .map(d -> {
                    String childText = d.getText() != null ? d.getText() : "";
                    Map<String, Object> meta = d.getMetadata() != null ? d.getMetadata() : Map.of();

                    // 父子索引模式：优先展示父块文本作为 text，子块文本放入 metadata.childText
                    String displayText;
                    Map<String, Object> displayMeta;
                    if (hasParentChild && meta.get("parentText") instanceof String pt && !pt.isBlank()) {
                        displayText = pt;  // 响应的 text 字段展示父块文本（完整上下文）
                        displayMeta = new HashMap<>(meta);
                        displayMeta.put("childText", childText);  // 子块文本（实际匹配内容）保留在 metadata 中
                        displayMeta.remove("parentText");          // 避免重复：父块已是 text，无需再留在 metadata
                    } else {
                        displayText = childText;
                        displayMeta = meta;
                    }

                    return new RetrievedChunk(d.getId(), displayText, displayMeta);
                })
                .toList();

        String answer = null;
        if (Boolean.TRUE.equals(req.generateAnswer()) && chatModel != null && !chunks.isEmpty()) {
            if (hasParentChild) {
                // 父子索引模式：提取去重的父块文本作为大模型上下文
                List<String> parentTexts = extractUniqueParentTexts(docs);
                answer = generateAnswerWithParentContext(req.question(), parentTexts);
            } else {
                // 传统模式：直接用检索到的片段作为上下文
                answer = generateAnswer(req.question(), chunks);
            }
        }

        return new QaQueryResponse(answer, chunks);
    }

    /**
     * 从检索到的子块文档中提取去重的父块文本。
     * <p>
     * 多个子块可能属于同一个父块（共享相同的 parentId），
     * 此方法按 parentId 去重，保持检索顺序（相似度从高到低），
     * 返回不重复的父块文本列表。
     * </p>
     *
     * @param docs 检索到的子块文档列表
     * @return 去重后的父块文本列表，按首次出现顺序排列
     */
    private List<String> extractUniqueParentTexts(List<Document> docs) {
        // 使用 LinkedHashSet 按插入顺序去重
        Set<String> seenParentIds = new LinkedHashSet<>();
        List<String> parentTexts = new ArrayList<>();

        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (meta == null) continue;

            Object parentId = meta.get("parentId");
            Object parentText = meta.get("parentText");

            if (parentText instanceof String pt && !pt.isBlank()) {
                String pid = parentId != null ? parentId.toString() : pt; // 用 parentId 去重
                if (seenParentIds.add(pid)) {
                    parentTexts.add(pt);
                }
            }
        }
        return parentTexts;
    }

    /**
     * 从 Milvus 向量库中检索与问题最相关的文档片段。
     * <p>
     * 内部会将 question 文本通过 Embedding 模型（text-embedding-v3）转为 1024 维向量，
     * 然后在 Milvus 中执行 ANN（近似最近邻）搜索。
     * 如果底层是 MilvusVectorStore，会额外设置 nprobe=32 以提高召回率。
     * </p>
     * <p>
     * 在 PARENT_CHILD 模式下，检索的是子块（~100字），精准匹配用户问题。
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
     * 使用父块文本作为上下文，调用 Chat 大语言模型生成自然语言回答（PARENT_CHILD 模式专用）。
     * <p>
     * 与传统模式不同，此方法使用的是大的父块文本（~1000字）而非小的子块文本（~100字），
     * 提供更丰富的上下文背景，帮助大模型生成更准确、更完整的回答。
     * </p>
     *
     * @param question    用户的原始问题
     * @param parentTexts 去重后的父块文本列表，作为 Chat 模型的参考上下文
     * @return 模型生成的自然语言回答
     */
    private String generateAnswerWithParentContext(String question, List<String> parentTexts) {
        StringJoiner ctx = new StringJoiner("\n---\n");
        int i = 1;
        for (String pt : parentTexts) {
            ctx.add("[" + i++ + "] " + pt);
        }
        String system = "你是文档问答助手。请仅根据用户提供的参考片段回答问题；若片段不足以回答，请明确说明无法从文档中得出答案，不要编造。";
        String user = "参考片段（父块上下文）：\n" + ctx + "\n\n问题：" + question;

        return ChatClient.builder(chatModel)
                .build()
                .prompt()
                .system(system)
                .user(user)
                .call()
                .content();
    }

    /**
     * 调用 Chat 大语言模型（qwen-plus）生成自然语言回答（传统模式）。
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
