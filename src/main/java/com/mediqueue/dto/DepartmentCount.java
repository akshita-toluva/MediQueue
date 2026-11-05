package com.mediqueue.dto;

// One row of "active appointments per department"
public record DepartmentCount (String department, Long count){
}
