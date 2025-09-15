package com.example.rag.dto;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
public class QueryRequest {

    @NotBlank(message = "Query cannot be empty")
    private String query;

    @Min(value = 1, message = "Top K must be at least 1")
    @Max(value = 20, message = "Top K cannot exceed 20")
    private int topK = 5;

    private double similarityThreshold = 0.7;
    private boolean includeMetadata = true;
}