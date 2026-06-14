package com.rowingclub.backend.controller;

import com.rowingclub.backend.repository.AppSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final AppSettingRepository appSettingRepository;

    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> getPublicSettings() {
        Map<String, String> out = new java.util.HashMap<>();
        appSettingRepository.findById("student_next_day_only")
                .ifPresent(s -> out.put("student_next_day_only", s.getSettingValue()));
        appSettingRepository.findById("student_booking_hour")
                .ifPresent(s -> out.put("student_booking_hour", s.getSettingValue()));
        appSettingRepository.findById("allow_cancellations")
                .ifPresent(s -> out.put("allow_cancellations", s.getSettingValue()));
        appSettingRepository.findById("booking_hour_disabled")
                .ifPresent(s -> out.put("booking_hour_disabled", s.getSettingValue()));
        appSettingRepository.findById("disable_availability")
                .ifPresent(s -> out.put("disable_availability", s.getSettingValue()));
        return ResponseEntity.ok(out);
    }
}
