package com.satvault.controller;

import com.satvault.dto.DocumentDtos.*;
import com.satvault.model.*;
import com.satvault.repository.DocumentRepository;
import com.satvault.security.AuthHelper;
import com.satvault.service.ActivityService;
import com.satvault.service.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;
    private final ActivityService activityService;
    private final PasswordEncoder passwordEncoder;
    private final AuthHelper authHelper;

    public DocumentController(
            DocumentRepository documentRepository,
            StorageService storageService,
            ActivityService activityService,
            PasswordEncoder passwordEncoder,
            AuthHelper authHelper) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.activityService = activityService;
        this.passwordEncoder = passwordEncoder;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<DocumentListResponse> listDocuments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) DocumentCategory category,
            @RequestParam(defaultValue = "false") boolean encryptedOnly,
            @RequestParam(defaultValue = "false") boolean protectedOnly,
            @RequestParam(defaultValue = "newest") String sort) {

        User user = authHelper.getCurrentUser();
        Sort sortOrder = switch (sort) {
            case "oldest" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "name" -> Sort.by(Sort.Direction.ASC, "title");
            case "size" -> Sort.by(Sort.Direction.DESC, "fileSize");
            default -> Sort.by(Sort.Direction.DESC, "createdAt");
        };

        String trimmedQuery = (q == null || q.isBlank()) ? null : q.trim();
        List<Document> filtered = documentRepository.searchDocuments(
                user, category, trimmedQuery, encryptedOnly, protectedOnly, sortOrder);

        List<Document> allUserDocs = documentRepository.findByOwner(user);
        Map<String, Long> categoryCounts = new LinkedHashMap<>();
        for (DocumentCategory cat : DocumentCategory.values()) {
            categoryCounts.put(cat.name(), 0L);
        }
        long encryptedCount = 0;
        long protectedCount = 0;
        long totalBytes = 0;
        for (Document d : allUserDocs) {
            categoryCounts.merge(d.getCategory().name(), 1L, Long::sum);
            if (d.isEncrypted()) encryptedCount++;
            if (d.isPasswordProtected()) protectedCount++;
            totalBytes += d.getFileSize();
        }

        VaultStats stats = new VaultStats(
                allUserDocs.size(),
                encryptedCount,
                protectedCount,
                totalBytes,
                categoryCounts
        );

        List<DocumentResponse> docs = filtered.stream().map(DocumentResponse::from).toList();
        return ResponseEntity.ok(new DocumentListResponse(docs, stats));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "category", defaultValue = "OTHER") DocumentCategory category,
            @RequestParam(value = "tags", required = false) String tags,
            @RequestParam(value = "encrypt", defaultValue = "true") boolean encrypt,
            @RequestParam(value = "documentPassword", required = false) String documentPassword,
            HttpServletRequest request) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please select a file to upload"));
        }

        User user = authHelper.getCurrentUser();
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[\\\\/]+", "_")
                : "document";
        String resolvedTitle = (title != null && !title.isBlank()) ? title.trim() : originalFilename;
        String contentType = file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        String storedFilename = storageService.store(user.getId(), file.getBytes(), encrypt);

        Document doc = new Document();
        doc.setOwner(user);
        doc.setTitle(resolvedTitle.length() > 180 ? resolvedTitle.substring(0, 180) : resolvedTitle);
        doc.setDescription(description != null ? description.trim() : null);
        doc.setCategory(category);
        doc.setOriginalFilename(originalFilename);
        doc.setStoredFilename(storedFilename);
        doc.setContentType(contentType);
        doc.setFileSize(file.getSize());
        doc.setEncrypted(encrypt);
        doc.setTags(tags != null && !tags.isBlank() ? tags.trim() : null);

        boolean hasDocPassword = documentPassword != null && !documentPassword.isBlank();
        if (hasDocPassword) {
            doc.setPasswordProtected(true);
            doc.setDocumentPasswordHash(passwordEncoder.encode(documentPassword));
        }

        doc = documentRepository.save(doc);

        String details = String.format("Uploaded %s (%s)%s",
                originalFilename,
                encrypt ? "AES-256-GCM encrypted" : "standard storage",
                hasDocPassword ? " + password locked" : "");
        activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UPLOAD, details, request);

        if (hasDocPassword) {
            activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.PASSWORD_SET,
                    "Document access password configured on upload", request);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc));
    }

    @PostMapping("/{id}/verify-password")
    public ResponseEntity<?> verifyPassword(
            @PathVariable Long id,
            @RequestBody VerifyPasswordRequest body,
            HttpServletRequest request) {
        User user = authHelper.getCurrentUser();
        Document doc = documentRepository.findByIdAndOwner(id, user).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found"));
        }
        if (!doc.isPasswordProtected()) {
            return ResponseEntity.ok(Map.of("verified", true));
        }
        String candidate = body != null ? body.password() : null;
        if (candidate == null || !passwordEncoder.matches(candidate, doc.getDocumentPasswordHash())) {
            activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UNLOCK_FAILED,
                    "Incorrect document password entered", request);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Incorrect document password"));
        }
        activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UNLOCK_SUCCESS,
                "Document unlocked with password", request);
        return ResponseEntity.ok(Map.of("verified", true));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<?> downloadDocument(
            @PathVariable Long id,
            @RequestHeader(value = "X-Document-Password", required = false) String headerPassword,
            @RequestParam(value = "docPassword", required = false) String paramPassword,
            HttpServletRequest request) throws IOException {
        return serveDocument(id, headerPassword != null ? headerPassword : paramPassword, true, request);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<?> previewDocument(
            @PathVariable Long id,
            @RequestHeader(value = "X-Document-Password", required = false) String headerPassword,
            @RequestParam(value = "docPassword", required = false) String paramPassword,
            HttpServletRequest request) throws IOException {
        return serveDocument(id, headerPassword != null ? headerPassword : paramPassword, false, request);
    }

    private ResponseEntity<?> serveDocument(Long id, String password, boolean attachment, HttpServletRequest request) throws IOException {
        User user = authHelper.getCurrentUser();
        Document doc = documentRepository.findByIdAndOwner(id, user).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found"));
        }

        if (doc.isPasswordProtected()) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Document is password protected", "passwordRequired", true));
            }
            if (!passwordEncoder.matches(password, doc.getDocumentPasswordHash())) {
                activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UNLOCK_FAILED,
                        "Failed password attempt during " + (attachment ? "download" : "preview"), request);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "Incorrect document password", "passwordRequired", true));
            }
        }

        byte[] content = storageService.load(user.getId(), doc.getStoredFilename(), doc.isEncrypted());

        activityService.record(
                user,
                doc.getId(),
                doc.getTitle(),
                attachment ? ActivityType.DOWNLOAD : ActivityType.PREVIEW,
                (doc.isEncrypted() ? "Decrypted and " : "") + (attachment ? "downloaded " : "previewed ") + doc.getOriginalFilename(),
                request
        );

        ContentDisposition disposition = ContentDisposition
                .builder(attachment ? "attachment" : "inline")
                .filename(doc.getOriginalFilename(), StandardCharsets.UTF_8)
                .build();

        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(doc.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(mediaType)
                .contentLength(content.length)
                .body(content);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateDocument(
            @PathVariable Long id,
            @Valid @RequestBody DocumentUpdateRequest req,
            HttpServletRequest request) {
        User user = authHelper.getCurrentUser();
        Document doc = documentRepository.findByIdAndOwner(id, user).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found"));
        }

        boolean metadataChanged = false;
        if (req.title() != null && !req.title().isBlank()) {
            doc.setTitle(req.title().trim());
            metadataChanged = true;
        }
        if (req.description() != null) {
            doc.setDescription(req.description().trim());
            metadataChanged = true;
        }
        if (req.category() != null) {
            doc.setCategory(req.category());
            metadataChanged = true;
        }
        if (req.tags() != null) {
            doc.setTags(req.tags().trim());
            metadataChanged = true;
        }

        boolean removingPassword = Boolean.TRUE.equals(req.removePassword());
        boolean settingNewPassword = req.newPassword() != null && !req.newPassword().isBlank();

        if (removingPassword || settingNewPassword) {
            if (doc.isPasswordProtected()) {
                if (req.currentPassword() == null || !passwordEncoder.matches(req.currentPassword(), doc.getDocumentPasswordHash())) {
                    activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UNLOCK_FAILED,
                            "Incorrect current password while updating lock settings", request);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Map.of("error", "Current document password is incorrect"));
                }
            }
            if (removingPassword) {
                doc.setPasswordProtected(false);
                doc.setDocumentPasswordHash(null);
                activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.PASSWORD_REMOVED,
                        "Removed document password protection", request);
            } else {
                doc.setPasswordProtected(true);
                doc.setDocumentPasswordHash(passwordEncoder.encode(req.newPassword()));
                activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.PASSWORD_SET,
                        "Updated document password protection", request);
            }
        }

        doc = documentRepository.save(doc);
        if (metadataChanged) {
            activityService.record(user, doc.getId(), doc.getTitle(), ActivityType.UPDATE,
                    "Updated document metadata / category", request);
        }

        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(@PathVariable Long id, HttpServletRequest request) {
        User user = authHelper.getCurrentUser();
        Document doc = documentRepository.findByIdAndOwner(id, user).orElse(null);
        if (doc == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Document not found"));
        }

        String title = doc.getTitle();
        String filename = doc.getOriginalFilename();
        storageService.delete(user.getId(), doc.getStoredFilename());
        documentRepository.delete(doc);

        activityService.record(user, id, title, ActivityType.DELETE,
                "Permanently deleted " + filename, request);

        return ResponseEntity.ok(Map.of("deleted", true));
    }
}
