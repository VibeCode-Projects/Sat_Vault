package com.satvault.repository;

import com.satvault.model.Document;
import com.satvault.model.DocumentCategory;
import com.satvault.model.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByIdAndOwner(Long id, User owner);

    List<Document> findByOwner(User owner);

    @Query("""
        SELECT d FROM Document d
        WHERE d.owner = :owner
          AND (:category IS NULL OR d.category = :category)
          AND (:encryptedOnly = false OR d.encrypted = true)
          AND (:protectedOnly = false OR d.passwordProtected = true)
          AND (:q IS NULL OR :q = '' OR
               LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR
               LOWER(d.originalFilename) LIKE LOWER(CONCAT('%', :q, '%')) OR
               LOWER(COALESCE(d.description, '')) LIKE LOWER(CONCAT('%', :q, '%')) OR
               LOWER(COALESCE(d.tags, '')) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    List<Document> searchDocuments(
            @Param("owner") User owner,
            @Param("category") DocumentCategory category,
            @Param("q") String q,
            @Param("encryptedOnly") boolean encryptedOnly,
            @Param("protectedOnly") boolean protectedOnly,
            Sort sort
    );
}
