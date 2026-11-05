package com.mediqueue.dto;

// What the admin dashboard shows per department.
// patientsPerAvailableDoctor is Double so it can be null = "no doctor available" (overload signal)
public record DepartmentLoadResponse (String department,
                                     long activeAppointments,
                                     long totalDoctors,
                                     long availableDoctors,
                                     Double patientsPerAvailableDoctor)
{

}
