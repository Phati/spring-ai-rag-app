package com.example.rag.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotNull;

@Data
public class DocumentUploadRequest {

    @NotNull(message = "File is required")
    private MultipartFile file;

    private String description;

    private boolean enableChunking = true;

    private int chunkSize = 1000;

    private int chunkOverlap = 200;
}