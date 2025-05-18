package com.erencsahin.kafkaconsumerpostgresql.repository;

import com.erencsahin.kafkaconsumerpostgresql.dto.RateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RateRepository extends JpaRepository<RateEntity, Long> {
}

