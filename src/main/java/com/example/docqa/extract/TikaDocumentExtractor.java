package com.example.docqa.extract;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaDocumentExtractor {

    private final Parser parser = new AutoDetectParser();

    /**
     * 使用 Tika 从 PDF、TXT、Office 等流中提取纯文本（-1 表示不限制处理器内字符数）。
     */
    public String extractText(InputStream inputStream, String filename) throws IOException, TikaException, SAXException {
        Metadata metadata = new Metadata();
        if (filename != null && !filename.isBlank()) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        BodyContentHandler handler = new BodyContentHandler(-1);
        parser.parse(inputStream, handler, metadata, new ParseContext());
        return handler.toString() != null ? handler.toString().strip() : "";
    }
}
