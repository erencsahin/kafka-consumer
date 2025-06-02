package com.erencsahin.kafkaconsumerpostgresql.service;

import com.erencsahin.kafkaconsumerpostgresql.dto.*;
import com.erencsahin.kafkaconsumerpostgresql.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private static final Logger logger = LogManager.getLogger(KafkaConsumerService.class);
    private final RateRepository rateRepository;

    @KafkaListener(topics = "avg-data", groupId = "avg-consumers", containerFactory = "kafkaListenerContainerFactory")
    public void consumer(@Payload Rate rate,
                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                         @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
                         @Header(KafkaHeaders.OFFSET) Long offset,
                         Acknowledgment acknowledgment) {
        try {
            logger.info("Kafka mesajı alındı - Topic: {}, Partition: {}, Offset: {}, Rate: {}",
                    topic, partition, offset, rate);

            RateEntity rateEntity = new RateEntity(
                    null,
                    rate.getSymbol(),
                    rate.getAsk(),
                    rate.getBid(),
                    LocalDateTime.parse(rate.getTimestamp()),
                    LocalDateTime.now()
            );

            rateRepository.save(rateEntity);
            logger.info("Veri postgresql'e kaydedildi - Symbol: {}", rate.getSymbol());

            // Manuel acknowledgment
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                logger.debug("Kafka mesajı acknowledge edildi - Offset: {}", offset);
            }

        } catch (Exception e) {
            logger.error("Kafka mesaj işleme hatası - Rate: {}, Error: {}", rate, e.getMessage(), e);
            // Acknowledgment yapma - mesaj tekrar işlenecek
            throw e; // Bu sayede mesaj retry olur
        }
    }
}