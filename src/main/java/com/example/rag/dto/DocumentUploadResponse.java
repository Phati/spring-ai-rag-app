package com.example.rag.dto;

import com.example.rag.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponse {

    private Long documentId;
    private String filename;
    private String originalFilename;
    private Long fileSize;
    private String contentType;
    private Document.ProcessingStatus status;
    private int totalChunks;
    private LocalDateTime uploadedAt;
    private String message;
}