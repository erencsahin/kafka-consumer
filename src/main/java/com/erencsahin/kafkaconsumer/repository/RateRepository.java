package com.erencsahin.kafkaconsumer.repository;

import com.erencsahin.kafkaconsumer.dto.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateRepository extends JpaRepository<RateEntity, Long> {
}

