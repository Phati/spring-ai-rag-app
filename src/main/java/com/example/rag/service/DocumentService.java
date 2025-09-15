package com.example.rag.service;

import com.example.rag.dto.DocumentUploadRequest;
import com.example.rag.dto.DocumentUploadResponse;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;

    @Transactional
    public DocumentUploadResponse uploadAndProcessDocument(DocumentUploadRequest request) {
        MultipartFile file = request.getFile();

        try {
            log.info("Starting document upload: {}", file.getOriginalFilename());

            validateFile(file);

            // Create document entity
            Document document = createDocumentEntity(file);
            document.setStatus(Document.ProcessingStatus.UPLOADING);
            document = documentRepository.save(document);

            // Process PDF with Spring AI
            document.setStatus(Document.ProcessingStatus.PROCESSING);
            document = documentRepository.save(document);

            // Use Spring AI PDF Reader
            byte[] fileBytes = file.getBytes();
            ByteArrayResource resource = new ByteArrayResource(fileBytes);

            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<org.springframework.ai.document.Document> documents = pdfReader.get();

            // Clean metadata and extract text safely
            List<org.springframework.ai.document.Document> cleanedDocuments = cleanDocumentMetadata(documents);
            String extractedText = extractFullTextSafely(cleanedDocuments);
            document.setExtractedText(extractedText);

            // Split into chunks
            TokenTextSplitter textSplitter = new TokenTextSplitter(
                    request.getChunkSize(),
                    request.getChunkOverlap(),
                    5, 10000, true
            );
            List<org.springframework.ai.document.Document> chunks = textSplitter.apply(cleanedDocuments);

            // Add metadata to chunks with null-safe handling
            for (int i = 0; i < chunks.size(); i++) {
                org.springframework.ai.document.Document chunk = chunks.get(i);
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("filename", document.getFilename());
                metadata.put("original_filename", document.getOriginalFilename());
                metadata.put("chunk_index", i);
                metadata.put("document_id", document.getId().toString());
                metadata.put("source", "pdf");
                metadata.put("page_number", i); // Add page tracking

                // Clear existing metadata and set clean metadata
                chunk.getMetadata().clear();
                chunk.getMetadata().putAll(metadata);
            }

            // Store in vector database
            vectorStore.add(chunks);

            // Save chunk metadata to database
            List<DocumentChunk> documentChunks = createDocumentChunks(document, chunks);
            documentChunkRepository.saveAll(documentChunks);

            // Update status
            document.setStatus(Document.ProcessingStatus.COMPLETED);
            document = documentRepository.save(document);

            log.info("Successfully processed document: {} with {} chunks",
                    document.getFilename(), documentChunks.size());

            return DocumentUploadResponse.builder()
                    .documentId(document.getId())
                    .filename(document.getFilename())
                    .originalFilename(document.getOriginalFilename())
                    .fileSize(document.getFileSize())
                    .contentType(document.getContentType())
                    .status(document.getStatus())
                    .totalChunks(documentChunks.size())
                    .uploadedAt(document.getCreatedAt())
                    .message("Document processed successfully")
                    .build();

        } catch (Exception e) {
            log.error("Error processing document: {}", file.getOriginalFilename(), e);

            // Update document status to failed
            try {
                Document doc = documentRepository.findByFilename(generateUniqueFilename(file.getOriginalFilename()))
                        .orElse(null);
                if (doc != null) {
                    doc.setStatus(Document.ProcessingStatus.FAILED);
                    documentRepository.save(doc);
                }
            } catch (Exception ex) {
                log.error("Failed to update document status", ex);
            }

            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    /**
     * Clean document metadata to remove null values that cause formatting issues
     */
    private List<org.springframework.ai.document.Document> cleanDocumentMetadata(List<org.springframework.ai.document.Document> documents) {
        List<org.springframework.ai.document.Document> cleanedDocuments = new ArrayList<>();

        for (org.springframework.ai.document.Document doc : documents) {
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
            org.springframework.ai.document.Document cleanedDoc = new org.springframework.ai.document.Document(rawContent, cleanMetadata);
            cleanedDocuments.add(cleanedDoc);
        }

        return cleanedDocuments;
    }

    /**
     * Extract raw content from document without triggering formatter
     */
    private String getRawContentSafely(org.springframework.ai.document.Document doc) {
        try {
            // Try to access the private content field directly using reflection
            java.lang.reflect.Field contentField = org.springframework.ai.document.Document.class.getDeclaredField("content");
            contentField.setAccessible(true);
            Object content = contentField.get(doc);
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            log.warn("Could not access content field directly, using alternative method", e);

            // Fallback: try to get content through toString or other means
            try {
                // Create a temporary document with empty metadata to avoid formatter issues
                org.springframework.ai.document.Document tempDoc = new org.springframework.ai.document.Document(doc.toString(), new HashMap<>());
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
    private String extractFullTextSafely(List<org.springframework.ai.document.Document> documents) {
        StringBuilder fullText = new StringBuilder();
        for (org.springframework.ai.document.Document doc : documents) {
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

    private Document createDocumentEntity(MultipartFile file) {
        String uniqueFilename = generateUniqueFilename(file.getOriginalFilename());

        return Document.builder()
                .filename(uniqueFilename)
                .originalFilename(file.getOriginalFilename())
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .status(Document.ProcessingStatus.UPLOADING)
                .build();
    }

    private String generateUniqueFilename(String originalFilename) {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return uuid + "_" + originalFilename;
    }

    private List<DocumentChunk> createDocumentChunks(Document document,
                                                     List<org.springframework.ai.document.Document> aiDocuments) {
        List<DocumentChunk> chunks = new ArrayList<>();

        for (int i = 0; i < aiDocuments.size(); i++) {
            org.springframework.ai.document.Document aiDoc = aiDocuments.get(i);

            // Use safe content extraction for chunks too
            String chunkContent = getRawContentSafely(aiDoc);

            DocumentChunk chunk = DocumentChunk.builder()
                    .document(document)
                    .content(chunkContent)
                    .chunkIndex(i)
                    .vectorStoreId(aiDoc.getId())
                    .build();

            chunks.add(chunk);
        }

        return chunks;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAllProcessedDocuments();
    }

    public Document getDocumentById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = getDocumentById(id);
        documentChunkRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
        log.info("Deleted document: {}", document.getFilename());
    }
}