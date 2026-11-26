package com.satvault.dto;

import com.satvault.model.ActivityType;
import com.satvault.model.Document;
import com.satvault.model.DocumentActivity;
import com.satvault.model.DocumentCategory;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DocumentDtos {

    public record DocumentResponse(
            Long id,
            String title,
            String description,
            DocumentCategory category,
            String categoryLabel,
            String originalFilename,
            String contentType,
            long fileSize,
            boolean encrypted,
            boolean passwordProtected,
            String tags,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static DocumentResponse from(Document d) {
            return new DocumentResponse(
                    d.getId(),
                    d.getTitle(),
                    d.getDescription(),
                    d.getCategory(),
                    d.getCategory().getLabel(),
                    d.getOriginalFilename(),
                    d.getContentType(),
                    d.getFileSize(),
                    d.isEncrypted(),
                    d.isPasswordProtected(),
                    d.getTags(),
                    d.getCreatedAt(),
                    d.getUpdatedAt()
            );
        }
    }

    public record VaultStats(
            long totalCount,
            long encryptedCount,
            long protectedCount,
            long totalBytes,
            Map<String, Long> categoryCounts
    ) {}

    public record DocumentListResponse(
            List<DocumentResponse> documents,
            VaultStats stats
    ) {}

    public record DocumentUpdateRequest(
            @Size(min = 1, max = 180, message = "Title must be 1-180 characters")
            String title,
            @Size(max = 500, message = "Description max 500 characters")
            String description,
            DocumentCategory category,
            @Size(max = 255, message = "Tags max 255 characters")
            String tags,
            Boolean removePassword,
            String newPassword,
            String currentPassword
    ) {}

    public record VerifyPasswordRequest(String password) {}

    public record ActivityResponse(
            Long id,
            Long documentId,
            String documentTitle,
            ActivityType action,
            String details,
            String ipAddress,
            Instant createdAt
    ) {
        public static ActivityResponse from(DocumentActivity a) {
            return new ActivityResponse(
                    a.getId(),
                    a.getDocumentId(),
                    a.getDocumentTitle(),
                    a.getAction(),
                    a.getDetails(),
                    a.getIpAddress(),
                    a.getCreatedAt()
            );
        }
    }
}
