package com.example.rag.controller;

import com.example.rag.dto.DocumentUploadRequest;
import com.example.rag.dto.DocumentUploadResponse;
import com.example.rag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Document Management", description = "APIs for managing PDF documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and process PDF document",
            description = "Upload a PDF document for processing and embedding generation")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(@Valid @ModelAttribute DocumentUploadRequest request) {

        log.info("Received document upload request: {}", request.getFile().getOriginalFilename());

        DocumentUploadResponse response = documentService.uploadAndProcessDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}