package com.satvault.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "document_activities", indexes = {
    @Index(name = "idx_activity_user_time", columnList = "user_id, createdAt")
})
public class DocumentActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private Long documentId;

    @Column(nullable = false, length = 180)
    private String documentTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ActivityType action;

    @Column(length = 300)
    private String details;

    @Column(length = 64)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public DocumentActivity() {}

    public DocumentActivity(User user, Long documentId, String documentTitle, ActivityType action, String details, String ipAddress) {
        this.user = user;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Long getDocumentId() { return documentId; }
    public String getDocumentTitle() { return documentTitle; }
    public ActivityType getAction() { return action; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
