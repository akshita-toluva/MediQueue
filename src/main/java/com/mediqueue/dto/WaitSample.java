package com.mediqueue.dto;

// One completed visit: what we told the patient vs what really happened (minutes)
public record WaitSample(String department, Integer estimated, Integer actual) {
}
