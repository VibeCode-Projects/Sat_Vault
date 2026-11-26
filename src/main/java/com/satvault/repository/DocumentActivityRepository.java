package com.satvault.repository;

import com.satvault.model.ActivityType;
import com.satvault.model.DocumentActivity;
import com.satvault.model.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentActivityRepository extends JpaRepository<DocumentActivity, Long> {
    List<DocumentActivity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<DocumentActivity> findByUserAndActionOrderByCreatedAtDesc(User user, ActivityType action, Pageable pageable);
    List<DocumentActivity> findByUserAndDocumentIdOrderByCreatedAtDesc(User user, Long documentId, Pageable pageable);
}
