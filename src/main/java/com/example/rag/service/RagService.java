package com.example.rag.service;

import com.example.rag.dto.QueryRequest;
import com.example.rag.dto.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public QueryResponse processQuery(QueryRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Processing RAG query: {}", request.getQuery());

            // Create search request with Spring AI 1.0.1 API
            SearchRequest searchRequest = SearchRequest.builder().query(request.getQuery())
                    .topK(request.getTopK())
                    .similarityThreshold(request.getSimilarityThreshold())
                    .build();

            // Retrieve relevant documents
            List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);

            if (relevantDocs.isEmpty()) {
                return QueryResponse.builder()
                        .answer("I couldn't find any relevant information to answer your question.")
                        .query(request.getQuery())
                        .sources(List.of())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .totalSources(0)
                        .build();
            }

            // Build context from retrieved documents
            String context = buildContext(relevantDocs);

            // Generate answer using ChatClient
            String prompt = buildPrompt(request.getQuery(), context);
            String answer = chatClient.prompt(prompt).call().content();

            // Build response with sources
            List<QueryResponse.RetrievedDocument> sources = buildSources(relevantDocs);

            double processingTime = System.currentTimeMillis() - startTime;

            log.info("Successfully processed query in {}ms", processingTime);

            return QueryResponse.builder()
                    .answer(answer)
                    .query(request.getQuery())
                    .sources(sources)
                    .processingTimeMs(processingTime)
                    .totalSources(sources.size())
                    .build();

        } catch (Exception e) {
            log.error("Error processing RAG query: {}", request.getQuery(), e);
            throw new RuntimeException("Failed to process query: " + e.getMessage(), e);
        }
    }

    private String buildContext(List<Document> documents) {
        return documents.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n"));
    }

    private String buildPrompt(String query, String context) {
        return String.format("""
                You are a helpful AI assistant. Answer the user's question based on the provided context.
                
                Context:
                %s
                
                Question: %s
                
                Instructions:
                - Provide a comprehensive and accurate answer based on the context
                - If the context doesn't contain enough information, say so
                - Be concise but thorough
                - Use a professional and helpful tone
                
                Answer:""", context, query);
    }

    private List<QueryResponse.RetrievedDocument> buildSources(List<Document> documents) {
        return documents.stream()
                .map(doc -> {
                    String filename = (String) doc.getMetadata().get("original_filename");
                    Object chunkIndexObj = doc.getMetadata().get("chunk_index");
                    Integer chunkIndex = chunkIndexObj != null ? (Integer) chunkIndexObj : 0;

                    return QueryResponse.RetrievedDocument.builder()
                            .filename(filename != null ? filename : "Unknown")
                            .content(truncateContent(doc.getFormattedContent(), 500))
                            .chunkIndex(chunkIndex)
                            .similarityScore(1.0) // Similarity score from vector store
                            .build();
                })
                .collect(Collectors.toList());
    }

    private String truncateContent(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}