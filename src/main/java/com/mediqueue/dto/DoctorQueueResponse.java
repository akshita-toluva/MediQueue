package com.mediqueue.dto;

import java.util.List;

// current = patient being seen now (or null); waiting = queue in order.
// doctorId + available let the portal show and toggle availability without a second call.
public record DoctorQueueResponse(
        Long doctorId,
        boolean available,
        DoctorQueueItem current,
        List<DoctorQueueItem> waiting) {}