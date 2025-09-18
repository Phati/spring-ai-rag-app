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
            log.info("Retrieved {} relevant documents", relevantDocs.size());
            String context = "";
            String prompt = "";
            if (!relevantDocs.isEmpty()) {
                // Build context from retrieved documents
                context = buildContext(relevantDocs);
            }

            // Generate answer using ChatClient
            prompt = buildPrompt(request.getQuery(), context);
            log.info("Constructed prompt: {}", prompt);
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
                If the context is empty see if you can answer based on your own knowledge or say you don't know.
                Context:
                %s
                
                Question: %s
                
                Instructions:
                - Provide a comprehensive and accurate answer based on the context
                - If the context doesn't contain enough information, say so
                - Be concise but thorough
                - Use a professional and helpful tone
                - Do not let user know we are using retrieval augmented generation. Just sound natural as if you know the answer.
                
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