package com.mediqueue.Service;

import com.mediqueue.dsaLayer.HashMapDoctorCache;
import com.mediqueue.dto.AppointmentRequest;
import com.mediqueue.entity.*;
import com.mediqueue.exception.ConflictException;
import com.mediqueue.repository.AppointmentRepository;
import com.mediqueue.repository.DoctorRepository;
import com.mediqueue.repository.QueueRepository;
import com.mediqueue.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

// INTEGRATION test: real Spring app + real Postgres + real Valkey.
// Proves the doctor-row lock (SELECT ... FOR UPDATE) keeps the queue correct when requests arrive at the same moment.
// NOT @Transactional on purpose: each thread must run in its own committed transaction, like real HTTP requests.
// Everything created here is deleted in @AfterEach.
@SpringBootTest
class AppointmentConcurrencyTest {

    @Autowired AppointmentService appointmentService;
    @Autowired UserRepository userRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired QueueRepository queueRepository;
    @Autowired HashMapDoctorCache doctorAvailabilityCache;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);   // unique emails per run
    private final List<User> createdUsers = new ArrayList<>();
    private User doctorUser;
    private Doctor doctor;

    @BeforeEach
    void createDoctor() {
        doctorUser = saveUser("conc-doctor", Role.DOCTOR);
        doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).department("ConcurrencyTest").available(true).avgConsultationTime(10).build());
    }

    @AfterEach
    void cleanUp() {
        // Delete children before parents (foreign keys): queue entries → appointments → doctor → users
        List<Appointment> appointments = appointmentRepository.findAll().stream()
                .filter(a -> a.getDoctor().getId().equals(doctor.getId())).toList();
        List<Long> ids = appointments.stream().map(Appointment::getId).toList();
        queueRepository.deleteAll(queueRepository.findByAppointmentIdIn(ids));
        appointmentRepository.deleteAll(appointments);
        doctorRepository.delete(doctor);
        userRepository.deleteAll(createdUsers);
        doctorAvailabilityCache.evict(doctor.getId());   // don't leave a cache entry for a deleted doctor
    }

    @Test
    void simultaneousBookings_getUniqueQueuePositions() throws Exception {
        // 8, not more: each waiting thread holds a DB connection, and Hikari's default pool is 10
        int patients = 8;
        List<Callable<?>> bookings = new ArrayList<>();
        for (int i = 0; i < patients; i++) {
            User p = saveUser("conc-patient" + i, Role.PATIENT);
            bookings.add(() -> appointmentService.bookAppointment(request("mild fever"), p));
        }

        List<Throwable> errors = runAtTheSameTime(bookings);

        assertTrue(errors.isEmpty(), "Bookings failed: " + errors);
        List<Integer> positions = appointmentRepository
                .findByDoctorIdAndStatus(doctor.getId(), AppointmentStatus.PENDING)
                .stream().map(Appointment::getQueuePosition).sorted().toList();
        // Exactly 1..8: no duplicates, no gaps
        assertEquals(IntStream.rangeClosed(1, patients).boxed().toList(), positions);
    }

    @Test
    void twoSimultaneousCallNext_onlyOnePatientIsCalledIn() throws Exception {
        for (int i = 0; i < 2; i++) {
            appointmentService.bookAppointment(request("mild fever"), saveUser("conc-waiting" + i, Role.PATIENT));
        }

        // Like a double-click, or two open tabs, pressing "Call next" at the same moment
        List<Throwable> errors = runAtTheSameTime(List.<Callable<?>>of(
                () -> appointmentService.callNext(doctorUser),
                () -> appointmentService.callNext(doctorUser)));

        assertEquals(1, errors.size(), "Exactly one call should be refused");
        assertInstanceOf(ConflictException.class, errors.get(0));
        assertEquals(1, appointmentRepository.countByDoctorIdAndStatus(doctor.getId(), AppointmentStatus.IN_PROGRESS));
    }

    // ---------- helpers ----------

    // Starts all tasks, holds them at a gate, then releases them together → real overlap.
    // Returns the exceptions thrown (an empty list = all succeeded).
    private List<Throwable> runAtTheSameTime(List<Callable<?>> tasks) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<?> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();      // "I'm at the start line"
                go.await();             // wait for the starting gun
                return task.call();
            }));
        }
        ready.await();                  // everyone is waiting at the gate...
        go.countDown();                 // ...GO
        List<Throwable> errors = new ArrayList<>();
        for (Future<Object> f : futures) {
            try {
                f.get(30, java.util.concurrent.TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                errors.add(e.getCause());   // the real exception the service threw
            } catch (TimeoutException e) {
                errors.add(e);              // a deadlock would show up here
            }
        }
        pool.shutdownNow();
        return errors;
    }

    private User saveUser(String prefix, Role role) {
        User u = userRepository.save(User.builder()
                .name(prefix).email(prefix + "." + runId + "@concurrency.test")
                .password("not-used").role(role).build());
        createdUsers.add(u);
        return u;
    }

    private AppointmentRequest request(String symptoms) {
        AppointmentRequest r = new AppointmentRequest();
        r.setDoctorId(doctor.getId());
        r.setSymptomDescription(symptoms);
        return r;
    }
}