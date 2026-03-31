package com.example.docqa.service;

import com.example.docqa.chunk.TextChunker;
import com.example.docqa.extract.TikaDocumentExtractor;
import org.apache.tika.exception.TikaException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DocumentIngestionService {

    private final TikaDocumentExtractor extractor;
    private final TextChunker textChunker;
    private final VectorStore vectorStore;

    public DocumentIngestionService(
            TikaDocumentExtractor extractor,
            TextChunker textChunker,
            VectorStore vectorStore) {
        this.extractor = extractor;
        this.textChunker = textChunker;
        this.vectorStore = vectorStore;
    }

    public int ingest(MultipartFile file) throws IOException, TikaException, SAXException {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload";
        String text;
        try (var in = file.getInputStream()) {
            text = extractor.extractText(in, filename);
        }
        if (text.isBlank()) {
            return 0;
        }
        List<String> chunks = textChunker.chunk(text);
        if (chunks.isEmpty()) {
            return 0;
        }
        String docId = UUID.randomUUID().toString();
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String chunkId = docId + "#" + i;
            Map<String, Object> meta = Map.of(
                    "source", filename,
                    "docId", docId,
                    "chunkIndex", i,
                    "totalChunks", chunks.size()
            );
            docs.add(new Document(chunkId, chunks.get(i), meta));
        }
        vectorStore.add(docs);
        return chunks.size();
    }
}
