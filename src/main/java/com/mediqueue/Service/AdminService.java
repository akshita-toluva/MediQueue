package com.mediqueue.Service;

import com.mediqueue.dto.DepartmentCount;
import com.mediqueue.dto.DepartmentLoadResponse;
import com.mediqueue.dto.DoctorCountByDepartment;
import com.mediqueue.dto.HourlyCount;
import com.mediqueue.dto.NoShowRateResponse;
import com.mediqueue.entity.AppointmentStatus;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)   // analytics never write; readOnly lets Hibernate skip dirty-checking
public class AdminService {

    private static final int MAX_DAYS = 365;   // stops ?days=100000 from scanning the whole table
    private static final List<AppointmentStatus> ACTIVE =
            List.of(AppointmentStatus.PENDING, AppointmentStatus.IN_PROGRESS);

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;

    public List<DepartmentLoadResponse> getDepartmentLoad() {
        // TODAY only. Nothing expires old PENDING rows, so counting all-time PENDING would
        // show patients who booked last week and never came as "in queue" today.
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();

        // department -> active appointments today
        Map<String, Long> activeByDept = new HashMap<>();
        for (DepartmentCount row : appointmentRepository.countActiveByDepartment(ACTIVE, startOfToday)) {
            activeByDept.put(row.department(), row.count());
        }

        // department -> (total doctors, available doctors)
        Map<String, DoctorCountByDepartment> doctorsByDept = new HashMap<>();
        for (DoctorCountByDepartment row : doctorRepository.countDoctorsByDepartment()) {
            doctorsByDept.put(row.department(), row);
        }

        // Union of both key sets: a department with patients but ZERO doctors must still appear,
        // because that's the most urgent row on the dashboard. TreeSet keeps it alphabetical.
        Set<String> departments = new TreeSet<>(activeByDept.keySet());
        departments.addAll(doctorsByDept.keySet());

        List<DepartmentLoadResponse> result = new ArrayList<>();
        for (String dept : departments) {
            long active = activeByDept.getOrDefault(dept, 0L);
            DoctorCountByDepartment docs = doctorsByDept.get(dept);
            long total = (docs == null) ? 0 : docs.total();
            long available = (docs == null) ? 0 : docs.available();
            // null (not 0, not infinity) when nobody is available: the frontend shows it as "no doctor"
            Double perDoctor = (available == 0) ? null : (double) active / available;
            result.add(new DepartmentLoadResponse(dept, active, total, available, perDoctor));
        }
        return result;
    }

    public List<HourlyCount> getPeakHours(int days) {
        // Array index = hour of day. Hours with no bookings return no SQL row, so they default to 0 here.
        long[] counts = new long[24];
        for (HourlyCount row : appointmentRepository.countBookingsByHour(since(days))) {
            counts[row.hour()] = row.count();
        }
        // Always return all 24 hours, so the Recharts bar chart (Day 12) never has gaps.
        List<HourlyCount> result = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            result.add(new HourlyCount(h, counts[h]));
        }
        return result;
    }

    public List<NoShowRateResponse> getNoShowRates(int days) {
        return appointmentRepository.noShowRatesByDepartment(since(days));
    }

    // Single validation point for the window size.
    // IllegalArgumentException → 400 via your existing GlobalExceptionHandler.
    private LocalDateTime since(int days) {
        if (days < 1 || days > MAX_DAYS) {
            throw new IllegalArgumentException("days must be between 1 and " + MAX_DAYS);
        }
        return LocalDate.now().minusDays(days).atStartOfDay();
    }
}