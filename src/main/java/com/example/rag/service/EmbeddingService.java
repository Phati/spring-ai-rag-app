package com.example.rag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    public List<Document> processAndStoreDocument(byte[] pdfContent, String filename,
                                                  int chunkSize, int chunkOverlap) {
        try {
            log.info("Processing PDF document: {}", filename);

            // Read PDF content
            ByteArrayResource resource = new ByteArrayResource(pdfContent);
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            List<Document> documents = pdfReader.get();

            // Split documents into chunks
            TokenTextSplitter textSplitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
            List<Document> chunks = textSplitter.apply(documents);

            // Add metadata
            for (int i = 0; i < chunks.size(); i++) {
                Document chunk = chunks.get(i);
                chunk.getMetadata().put("filename", filename);
                chunk.getMetadata().put("chunk_index", i);
                chunk.getMetadata().put("source", "pdf");
            }

            // Store in vector database
            vectorStore.add(chunks);

            log.info("Successfully processed and stored {} chunks for document: {}", chunks.size(), filename);
            return chunks;

        } catch (Exception e) {
            log.error("Error processing document: {}", filename, e);
            throw new RuntimeException("Failed to process document: " + e.getMessage(), e);
        }
    }

    public List<Document> searchSimilarDocuments(String query, int topK, double threshold) {
        try {
            log.debug("Searching for similar documents with query: {}", query);

            List<Document> results = vectorStore.similaritySearch(SearchRequest.builder().query(query).similarityThreshold(threshold).topK(topK).build());

            log.debug("Found {} similar documents", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching for similar documents", e);
            throw new RuntimeException("Failed to search documents: " + e.getMessage(), e);
        }
    }
}