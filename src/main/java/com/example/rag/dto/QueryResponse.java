package com.example.rag.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryResponse {

    private String answer;
    private String query;
    private List<RetrievedDocument> sources;
    private double processingTimeMs;
    private int totalSources;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RetrievedDocument {
        private Long documentId;
        private String filename;
        private String content;
        private double similarityScore;
        private int chunkIndex;
    }
}