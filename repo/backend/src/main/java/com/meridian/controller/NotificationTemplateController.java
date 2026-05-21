package com.meridian.controller;

import com.meridian.dto.NotificationTemplateUpdateRequest;
import com.meridian.entity.NotificationTemplate;
import com.meridian.exception.AppException;
import com.meridian.repository.NotificationTemplateRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/notification-templates")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class NotificationTemplateController {

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateController(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @GetMapping
    public ResponseEntity<List<NotificationTemplate>> listTemplates() {
        return ResponseEntity.ok(templateRepository.findAll());
    }

    @PutMapping("/{name}")
    public ResponseEntity<NotificationTemplate> updateTemplate(
            @PathVariable String name,
            @Valid @RequestBody NotificationTemplateUpdateRequest request) {

        NotificationTemplate template = templateRepository.findByName(name)
                .orElseThrow(() -> AppException.notFound("Notification template not found: " + name));

        template.setSubject(request.subject());
        template.setBody(request.body());
        template.setUpdatedAt(Instant.now());

        return ResponseEntity.ok(templateRepository.save(template));
    }
}
