package com.example.rag.repository;

import com.example.rag.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByFilename(String filename);

    List<Document> findByStatus(Document.ProcessingStatus status);

    @Query("SELECT d FROM Document d WHERE d.status = 'COMPLETED' ORDER BY d.createdAt DESC")
    List<Document> findAllProcessedDocuments();

    @Query("SELECT COUNT(d) FROM Document d WHERE d.status = ?1")
    long countByStatus(Document.ProcessingStatus status);
}