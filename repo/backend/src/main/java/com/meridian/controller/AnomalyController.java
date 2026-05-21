package com.meridian.controller;

import com.meridian.entity.Anomaly;
import com.meridian.repository.AnomalyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/anomalies")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AnomalyController {

    private final AnomalyRepository anomalyRepository;

    public AnomalyController(AnomalyRepository anomalyRepository) {
        this.anomalyRepository = anomalyRepository;
    }

    /**
     * Returns a paginated list of all detected anomalies.
     */
    @GetMapping
    public ResponseEntity<Page<Anomaly>> getAnomalies(Pageable pageable) {
        return ResponseEntity.ok(anomalyRepository.findAll(pageable));
    }
}
