package com.example.rag.controller;

import com.example.rag.dto.DocumentUploadRequest;
import com.example.rag.dto.DocumentUploadResponse;
import com.example.rag.entity.Document;
import com.example.rag.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

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
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @Valid @ModelAttribute DocumentUploadRequest request) {

        log.info("Received document upload request: {}", request.getFile().getOriginalFilename());

        DocumentUploadResponse response = documentService.uploadAndProcessDocument(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get all documents", description = "Retrieve list of all processed documents")
    public ResponseEntity<List<Document>> getAllDocuments() {
        List<Document> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID", description = "Retrieve a specific document by its ID")
    public ResponseEntity<Document> getDocumentById(
            @Parameter(description = "Document ID") @PathVariable Long id) {
        Document document = documentService.getDocumentById(id);
        return ResponseEntity.ok(document);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document", description = "Delete a document and its associated chunks")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID") @PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}