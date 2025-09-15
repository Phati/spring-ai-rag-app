package com.example.rag.controller;

import com.example.rag.dto.QueryRequest;
import com.example.rag.dto.QueryResponse;
import com.example.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@Slf4j
public class QueryController {

    private final RagService ragService;

    @PostMapping
    public ResponseEntity<QueryResponse> processQuery(@Valid @RequestBody QueryRequest request) {
        log.info("Received RAG query: {}", request.getQuery());

        QueryResponse response = ragService.processQuery(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Query service is healthy");
    }
}