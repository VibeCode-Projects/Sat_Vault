package com.satvault.service;

import com.satvault.model.ActivityType;
import com.satvault.model.DocumentActivity;
import com.satvault.model.User;
import com.satvault.repository.DocumentActivityRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class ActivityService {

    private final DocumentActivityRepository activityRepository;

    public ActivityService(DocumentActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    public void record(User user, Long documentId, String documentTitle, ActivityType action, String details, HttpServletRequest request) {
        String ip = extractClientIp(request);
        DocumentActivity activity = new DocumentActivity(user, documentId, documentTitle, action, details, ip);
        activityRepository.save(activity);
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
