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

@Service
public class QaService {

    private final VectorStore vectorStore;
    private final MilvusVectorStore milvusVectorStore;
    private final ChatModel chatModel;

    public QaService(
            VectorStore vectorStore,
            @Autowired(required = false) ChatModel chatModel) {
        this.vectorStore = vectorStore;
        this.milvusVectorStore = vectorStore instanceof MilvusVectorStore m ? m : null;
        this.chatModel = chatModel;
    }

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
