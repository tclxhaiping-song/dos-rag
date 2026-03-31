package com.example.docqa.web;

import com.example.docqa.service.QaService;
import com.example.docqa.web.dto.QaQueryRequest;
import com.example.docqa.web.dto.QaQueryResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/query")
    public QaQueryResponse query(@Valid @RequestBody QaQueryRequest request) {
        return qaService.query(request);
    }
}
