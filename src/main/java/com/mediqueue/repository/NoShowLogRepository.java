package com.mediqueue.repository;

import com.mediqueue.entity.NoShowLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoShowLogRepository extends JpaRepository<NoShowLog,Long> {
}
