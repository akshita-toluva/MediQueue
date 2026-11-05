package com.mediqueue.controller;

import com.mediqueue.Service.AdminService;
import com.mediqueue.dto.DepartmentLoadResponse;
import com.mediqueue.dto.HourlyCount;
import com.mediqueue.dto.NoShowRateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
// At CLASS level, not per method: any endpoint added here later is ADMIN-only automatically.
// Nobody can forget the guard on a new method.
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    // GET /api/admin/department-load
    // Live: active patients vs available doctors per department, today.
    @GetMapping("/department-load")
    public ResponseEntity<List<DepartmentLoadResponse>> getDepartmentLoad() {
        return ResponseEntity.ok(adminService.getDepartmentLoad());
    }

    // GET /api/admin/peak-hours?days=30
    // Bookings per hour of day over the last N days (default 30). Always 24 rows.
    @GetMapping("/peak-hours")
    public ResponseEntity<List<HourlyCount>> getPeakHours(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminService.getPeakHours(days));
    }

    // GET /api/admin/no-show-rates?days=30
    // No-show rate per department over the last N days (default 30).
    @GetMapping("/no-show-rates")
    public ResponseEntity<List<NoShowRateResponse>> getNoShowRates(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(adminService.getNoShowRates(days));
    }
}