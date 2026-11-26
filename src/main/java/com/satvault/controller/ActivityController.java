package com.satvault.controller;

import com.satvault.dto.DocumentDtos.ActivityResponse;
import com.satvault.model.ActivityType;
import com.satvault.model.DocumentActivity;
import com.satvault.model.User;
import com.satvault.repository.DocumentActivityRepository;
import com.satvault.security.AuthHelper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ActivityController {

    private final DocumentActivityRepository activityRepository;
    private final AuthHelper authHelper;

    public ActivityController(DocumentActivityRepository activityRepository, AuthHelper authHelper) {
        this.activityRepository = activityRepository;
        this.authHelper = authHelper;
    }

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityResponse>> listActivities(
            @RequestParam(required = false) ActivityType action,
            @RequestParam(defaultValue = "100") int limit) {
        User user = authHelper.getCurrentUser();
        int safeLimit = Math.min(Math.max(limit, 1), 250);
        PageRequest page = PageRequest.of(0, safeLimit);

        List<DocumentActivity> items = (action != null)
                ? activityRepository.findByUserAndActionOrderByCreatedAtDesc(user, action, page)
                : activityRepository.findByUserOrderByCreatedAtDesc(user, page);

        return ResponseEntity.ok(items.stream().map(ActivityResponse::from).toList());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "SatVault"));
    }
}
