package com.mediqueue.dto;

// Total vs available doctors in one department
public record DoctorCountByDepartment (String department, Long total, Long available){
}
