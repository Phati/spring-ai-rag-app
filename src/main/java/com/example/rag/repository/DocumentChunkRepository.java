package com.example.rag.repository;

import com.example.rag.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.document.id = ?1")
    List<DocumentChunk> findChunksByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    @Query("SELECT COUNT(dc) FROM DocumentChunk dc WHERE dc.document.id = ?1")
    long countChunksByDocumentId(Long documentId);
}