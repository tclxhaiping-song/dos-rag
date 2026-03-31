package com.example.docqa.web;

import com.example.docqa.service.DocumentIngestionService;
import com.example.docqa.web.dto.IngestResponse;
import org.apache.tika.exception.TikaException;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public IngestResponse upload(@RequestPart("file") MultipartFile file)
            throws IOException, TikaException, SAXException {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        int n = ingestionService.ingest(file);
        return new IngestResponse(name, n);
    }
}
