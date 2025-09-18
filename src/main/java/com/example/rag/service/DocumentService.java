package com.example.rag.service;

import com.example.rag.dto.DocumentUploadRequest;
import com.example.rag.dto.DocumentUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final VectorStore vectorStore;
    private final ContentSanitizationService sanitizationService;

    // Supported file types
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // DOCX
            "application/msword", // DOC
            "text/plain", // TXT
            "text/html", // HTML
            "application/xhtml+xml", // XHTML
            "text/csv", // CSV
            "application/rtf", // RTF
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // PPTX
            "application/vnd.ms-powerpoint" // PPT
    );

    // Optimized chunking parameters for better embeddings
    private static final int DEFAULT_CHUNK_SIZE = 800; // Reduced for better semantic coherence
    private static final int DEFAULT_CHUNK_OVERLAP = 100; // Increased overlap for context preservation
    private static final int MIN_CHUNK_LENGTH = 50; // Minimum meaningful chunk size
    private static final int MAX_CHUNK_LENGTH = 2000; // Maximum chunk size

    @Transactional
    public DocumentUploadResponse uploadAndProcessDocument(DocumentUploadRequest request) {
        MultipartFile file = request.getFile();
        try {
            log.info("Starting document upload: {} (type: {})", file.getOriginalFilename(), file.getContentType());
            validateFile(file);

            // Get optimal chunking parameters
            int chunkSize = request.getChunkSize() > 0 ? request.getChunkSize() : DEFAULT_CHUNK_SIZE;
            int chunkOverlap = request.getChunkOverlap() > 0 ? request.getChunkOverlap() : DEFAULT_CHUNK_OVERLAP;

            // Ensure chunk size is within bounds
            chunkSize = Math.max(MIN_CHUNK_LENGTH, Math.min(MAX_CHUNK_LENGTH, chunkSize));
            chunkOverlap = Math.min(chunkOverlap, chunkSize / 2); // Overlap shouldn't exceed half of chunk size

            byte[] fileBytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(fileBytes);

            List<Document> documents;
            
            // Use appropriate reader based on file type
            if ("application/pdf".equals(file.getContentType())) {
                log.info("Processing PDF document with PagePdfDocumentReader");
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
                documents = pdfReader.read();
            } else {
                log.info("Processing document with TikaDocumentReader for type: {}", file.getContentType());
                TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
                documents = tikaReader.read();
            }

            log.info("Extracted {} documents from file", documents.size());

            // Sanitize and clean content
            documents = sanitizeDocuments(documents, file.getContentType());

            // Split into optimized chunks
            TokenTextSplitter textSplitter = new TokenTextSplitter(
                    chunkSize,
                    chunkOverlap,
                    MIN_CHUNK_LENGTH, 
                    MAX_CHUNK_LENGTH, 
                    true
            );
            List<Document> chunks = textSplitter.apply(documents);

            // Filter out very small chunks
            chunks = filterMinimalChunks(chunks);

            log.info("Created {} chunks from {} documents", chunks.size(), documents.size());

            // Add enhanced metadata to chunks
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("original_filename", file.getOriginalFilename());
                metadata.put("chunk_index", i);
                metadata.put("source", "document");
                metadata.put("content_type", file.getContentType());
                metadata.put("file_size", file.getSize());
                metadata.put("chunk_size", chunkSize);
                metadata.put("total_chunks", chunks.size());
                metadata.put("processing_timestamp", System.currentTimeMillis());

                // Clear existing metadata and set clean metadata
                chunk.getMetadata().clear();
                chunk.getMetadata().putAll(metadata);
            }

            // Store in vector database
            vectorStore.add(chunks);

            log.info("Successfully processed and stored {} chunks for document: {}", chunks.size(), file.getOriginalFilename());

            return DocumentUploadResponse.builder()
                    .filename(file.getOriginalFilename())
                    .originalFilename(file.getOriginalFilename())
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .totalChunks(chunks.size())
                    .message("Document uploaded and processed successfully")
                    .build();

        } catch (Exception e) {
            log.error("Failed to process document: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Sanitize and clean document content for better embedding quality
     */
    private List<Document> sanitizeDocuments(List<Document> documents, String contentType) {
        List<Document> sanitizedDocs = new ArrayList<>();
        
        for (Document doc : documents) {
            try {
                String originalContent = getRawContentSafely(doc);
                String cleanContent = sanitizationService.extractAndCleanText(originalContent, contentType);
                
                if (cleanContent != null && !cleanContent.trim().isEmpty()) {
                    // Create new document with cleaned content
                    Document cleanDoc = new Document(cleanContent, new HashMap<>(doc.getMetadata()));
                    sanitizedDocs.add(cleanDoc);
                } else {
                    log.warn("Document content became empty after sanitization, skipping");
                }
            } catch (Exception e) {
                log.error("Error sanitizing document content", e);
                // Keep original if sanitization fails
                sanitizedDocs.add(doc);
            }
        }
        
        log.info("Sanitized {} documents, {} remained after cleaning", documents.size(), sanitizedDocs.size());
        return sanitizedDocs;
    }

    /**
     * Filter out chunks that are too small to be meaningful
     */
    private List<Document> filterMinimalChunks(List<Document> chunks) {
        List<Document> filteredChunks = new ArrayList<>();
        
        for (Document chunk : chunks) {
            String content = getRawContentSafely(chunk);
            if (content != null && content.trim().length() >= MIN_CHUNK_LENGTH) {
                filteredChunks.add(chunk);
            } else {
                log.debug("Filtered out small chunk: {} characters", content != null ? content.length() : 0);
            }
        }
        
        log.info("Filtered chunks: {} -> {}", chunks.size(), filteredChunks.size());
        return filteredChunks;
    }

    /**
     * Extract raw content from document without triggering formatter
     */
    private String getRawContentSafely(Document doc) {
        if (doc == null) {
            return "";
        }
        
        try {
            String content = doc.getFormattedContent();
            return content != null ? content : "";
        } catch (Exception e) {
            log.warn("Failed to get content safely, trying fallback approach", e);
            try {
                // Fallback approach
                return doc.toString();
            } catch (Exception ex) {
                log.error("Failed to extract content safely", ex);
                return ""; // Return empty string as last resort
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            throw new RuntimeException("Unsupported file type: " + contentType + 
                    ". Supported types: PDF, DOCX, DOC, TXT, HTML, CSV, RTF, PPTX, PPT");
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            throw new RuntimeException("File size cannot exceed 50MB");
        }

        log.info("File validation passed for: {} (type: {}, size: {} bytes)", 
                file.getOriginalFilename(), contentType, file.getSize());
    }

}