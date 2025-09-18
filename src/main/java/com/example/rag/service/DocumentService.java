package com.example.rag.service;

import com.example.rag.dto.DocumentUploadRequest;
import com.example.rag.dto.DocumentUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final VectorStore vectorStore;

    @Transactional
    public DocumentUploadResponse uploadAndProcessDocument(DocumentUploadRequest request) {
        MultipartFile file = request.getFile();
        try {
            log.info("Starting document upload: {}", file.getOriginalFilename());
            validateFile(file);

            // Use Spring AI PDF Reader
            byte[] fileBytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(fileBytes);

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.read();

            // Split into chunks
            TokenTextSplitter textSplitter = new TokenTextSplitter(
                    request.getChunkSize(),
                    request.getChunkOverlap(),
                    5, 10000, true
            );
            List<Document> chunks = textSplitter.apply(documents);

            // Add metadata to chunks with null-safe handling
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("original_filename", file.getOriginalFilename());
                metadata.put("chunk_index", i);
                metadata.put("source", "pdf");
                metadata.put("page_number", i); // Add page tracking

                // Clear existing metadata and set clean metadata
                chunk.getMetadata().clear();
                chunk.getMetadata().putAll(metadata);
            }

            // Store in vector database
            vectorStore.add(chunks);

        } catch (Exception e) {
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
        return DocumentUploadResponse.builder().message("Document uploaded successfully").build();
    }


    /**
     * Clean document metadata to remove null values that cause formatting issues
     */
    private List<Document> cleanDocumentMetadata(List<Document> documents) {
        List<Document> cleanedDocuments = new ArrayList<>();

        for (Document doc : documents) {
            // Create new metadata map without null values
            Map<String, Object> cleanMetadata = new HashMap<>();

            // Only add non-null metadata values
            if (doc.getMetadata() != null) {
                doc.getMetadata().forEach((key, value) -> {
                    if (value != null && key != null) {
                        cleanMetadata.put(key, value);
                    }
                });
            }

            // CRITICAL FIX: Use reflection to get content directly without formatting
            String rawContent = getRawContentSafely(doc);

            // Create new document with cleaned metadata and raw content
            Document cleanedDoc = new Document(rawContent, cleanMetadata);
            cleanedDocuments.add(cleanedDoc);
        }

        return cleanedDocuments;
    }

    /**
     * Extract raw content from document without triggering formatter
     */
    private String getRawContentSafely(Document doc) {
        try {
            // Try to access the private content field directly using reflection
            java.lang.reflect.Field contentField = Document.class.getDeclaredField("content");
            contentField.setAccessible(true);
            Object content = contentField.get(doc);
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            log.warn("Could not access content field directly, using alternative method", e);

            // Fallback: try to get content through toString or other means
            try {
                // Create a temporary document with empty metadata to avoid formatter issues
                Document tempDoc = new Document(doc.toString(), new HashMap<>());
                return tempDoc.toString();
            } catch (Exception ex) {
                log.error("Failed to extract content safely", ex);
                return ""; // Return empty string as last resort
            }
        }
    }

    /**
     * Safely extract full text without using getFormattedContent() that causes issues
     */
    private String extractFullTextSafely(List<Document> documents) {
        StringBuilder fullText = new StringBuilder();
        for (Document doc : documents) {
            // Use our safe content extraction method
            String content = getRawContentSafely(doc);
            if (content != null && !content.trim().isEmpty()) {
                fullText.append(content).append("\n");
            }
        }
        return fullText.toString();
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("File cannot be empty");
        }

        if (!"application/pdf".equals(file.getContentType())) {
            throw new RuntimeException("Only PDF files are supported");
        }

        if (file.getSize() > 50 * 1024 * 1024) {
            throw new RuntimeException("File size cannot exceed 50MB");
        }
    }

}