package com.example.docqa.service;

import com.example.docqa.chunk.TextChunker;
import com.example.docqa.config.ChunkProperties;
import com.example.docqa.extract.TikaDocumentExtractor;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文档导入服务。
 * <p>
 * 负责将上传的文件提取文本 → 切分片段 → 向量化存入 Milvus。
 * 支持 CHAR / SENTENCE / PARENT_CHILD 三种切分策略。
 * </p>
 * <p>
 * 注意：百炼平台 Embedding API 每批最多处理 {@value #EMBEDDING_BATCH_SIZE} 条文本，
 * 因此入库时会自动分批调用 VectorStore.add()。
 * </p>
 */
@Service
public class DocumentIngestionService {

    /**
     * 每批发送给 Embedding API 的最大文档数。
     * 百炼平台 text-embedding-v3 限制单次请求最多 10 条，超过会返回 400 错误。
     */
    private static final int EMBEDDING_BATCH_SIZE = 10;

    private final TikaDocumentExtractor extractor;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;
    private final ChunkProperties chunkProperties;

    public DocumentIngestionService(
            TikaDocumentExtractor extractor,
            TextChunker textChunker,
            VectorStore vectorStore,
            ChunkProperties chunkProperties) {
        this.extractor = extractor;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
        this.chunkProperties = chunkProperties;
    }

    /**
     * 导入文档：提取文本 → 切分 → 向量化入库。
     * <p>
     * 根据 app.chunk.strategy 配置选择切分方式：
     * <ul>
     *   <li>CHAR / SENTENCE：传统切分，每个片段独立入库</li>
     *   <li>PARENT_CHILD：父子索引切分，子块入库用于检索，父块文本存入子块 metadata 供大模型使用</li>
     * </ul>
     *
     * @param file 上传的文档文件
     * @return 入库的向量片段数量（PARENT_CHILD 模式下为子块数量）
     * @throws IOException   文件读取异常
     * @throws TikaException Tika 解析异常
     * @throws SAXException  XML 解析异常
     */
    public int ingest(MultipartFile file) throws IOException, TikaException, SAXException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String text;
        try (var in = file.getInputStream()) {
            text = extractor.extractText(in, filename);
        }
        if (text.isBlank()) {
            return 0;
        }

        String docId = UUID.randomUUID().toString();

        // 根据策略选择不同的入库流程
        if (chunkProperties.getStrategy() == ChunkProperties.Strategy.PARENT_CHILD) {
            return ingestParentChild(text, filename, docId);
        } else {
            return ingestFlat(text, filename, docId);
        }
    }

    /**
     * 传统扁平切分入库（CHAR / SENTENCE 策略）。
     * <p>
     * 每个片段独立存入向量库，片段既用于检索也用于喂给大模型。
     * </p>
     *
     * @param text     文档全文
     * @param filename 文件名
     * @param docId    文档唯一标识
     * @return 入库的片段数量
     */
    private int ingestFlat(String text, String filename, String docId) {
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            return 0;
        }

        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = UUID.randomUUID().toString();
            Map<String, Object> meta = Map.of(
                    "source", filename,
                    "docId", docId,
                    "chunkIndex", String.valueOf(i),
                    "totalChunks", String.valueOf(chunks.size())
            );
            docs.add(new Document(chunkId, chunks.get(i), meta));
        }
        addInBatches(docs);
        return chunks.size();
    }

    /**
     * 父子索引切分入库（PARENT_CHILD 策略）。
     * <p>
     * 处理流程：
     * <ol>
     *   <li>将全文切分为大父块（~1000字）+ 小子块（~100字）</li>
     *   <li>仅将子块存入 Milvus 向量库（子块文本用于 embedding 和相似度检索）</li>
     *   <li>在每个子块的 metadata 中存入对应的父块文本（parentText 字段）</li>
     *   <li>检索时通过子块精准匹配，取出 parentText 喂给大模型提供完整上下文</li>
     * </ol>
     *
     * @param text     文档全文
     * @param filename 文件名
     * @param docId    文档唯一标识
     * @return 入库的子块数量
     */
    private int ingestParentChild(String text, String filename, String docId) {
        List<TextChunker.ParentChildChunk> parentChildChunks = textChunker.chunkParentChild(text);
        if (parentChildChunks.isEmpty()) {
            return 0;
        }

        List<Document> docs = new ArrayList<>();
        int globalChildIndex = 0;

        for (int pi = 0; pi < parentChildChunks.size(); pi++) {
            TextChunker.ParentChildChunk pc = parentChildChunks.get(pi);
            String parentId = UUID.randomUUID().toString();

            for (int ci = 0; ci < pc.childTexts().size(); ci++) {
                String childId = UUID.randomUUID().toString();
                // 使用可变 Map，因为 Map.of() 不允许 null 值且字段数受限
                Map<String, Object> meta = new HashMap<>();
                meta.put("source", filename);
                meta.put("docId", docId);
                meta.put("parentId", parentId);                            // 父块标识，用于去重
                meta.put("parentIndex", String.valueOf(pi));               // 父块在文档中的序号
                meta.put("childIndex", String.valueOf(ci));                // 子块在父块中的序号
                meta.put("globalChildIndex", String.valueOf(globalChildIndex)); // 子块在文档中的全局序号
                meta.put("totalParents", String.valueOf(parentChildChunks.size()));
                meta.put("totalChildren", String.valueOf(pc.childTexts().size()));
                meta.put("parentText", pc.parentText());                   // ★ 父块完整文本，检索后提供给大模型

                // 子块文本用于 embedding → Milvus 检索
                docs.add(new Document(childId, pc.childTexts().get(ci), meta));
                globalChildIndex++;
            }
        }

        addInBatches(docs);
        return docs.size();
    }

    /**
     * 分批将文档列表写入向量库。
     * <p>
     * 百炼平台 Embedding API（text-embedding-v3）限制单次请求最多 10 条文本，
     * 超过会返回 HTTP 400 错误（batch size is invalid）。
     * 此方法将文档列表按 {@value #EMBEDDING_BATCH_SIZE} 为一批，逐批调用
     * {@link VectorStore#add(List)} 完成 Embedding + Milvus 入库。
     * </p>
     *
     * @param docs 待入库的文档列表（可以超过 10 条）
     */
    private void addInBatches(List<Document> docs) {
        for (int i = 0; i < docs.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, docs.size());
            List<Document> batch = docs.subList(i, end);
            vectorStore.add(batch);
        }
    }
}
