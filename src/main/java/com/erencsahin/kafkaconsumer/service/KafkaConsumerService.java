package com.erencsahin.kafkaconsumer.service;

import com.erencsahin.kafkaconsumer.dto.*;
import com.erencsahin.kafkaconsumer.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private final RateRepository rateRepository;
    private final OpenSearchService openSearchService;

    //kafka topic'deki veriyi dinleyen class.

    @KafkaListener(topics = "avg-data", groupId = "avg-consumers", containerFactory = "kafkaListenerContainerFactory")
    public void consumer(Rate rate){
        RateEntity rateEntity=new RateEntity(
                null,
                rate.getSymbol(),
                rate.getAsk(),
                rate.getBid(),
                LocalDateTime.parse(rate.getTimestamp()),
                LocalDateTime.now()
        );
        rateRepository.save(rateEntity);
        openSearchService.indexRate(rate);
    }

}
