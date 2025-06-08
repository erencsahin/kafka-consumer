package com.erencsahin.kafkaconsumerpostgresql.service;

import com.erencsahin.kafkaconsumerpostgresql.dto.Rate;
import com.erencsahin.kafkaconsumerpostgresql.dto.RateEntity;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class KafkaConsumerService {
    private static final Logger logger = LogManager.getLogger(KafkaConsumerService.class);
    private final RateRepository rateRepository;
    private static final Set<String> SYMBOLS = Set.of("USDTRY", "EURTRY", "GBPTRY");
    private final Map<String, Rate> lastValidRateMap = new ConcurrentHashMap<>();
    private static final double maxDiff = 0.01;
    private final Map<String, Integer> consecutiveOutlierCount = new ConcurrentHashMap<>();
    private static final int MAX_CONSECUTIVE_OUTLIERS = 5;

    @KafkaListener(
            topics = "avg-data",
            groupId = "avg-consumers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumer(
            @Payload Rate rate,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment
    ) {
        String symbol = rate.getSymbol();

        // 1) İzinli çift mi?
        if (!SYMBOLS.contains(symbol)) {
            logger.warn("Unknown symbol '{}' has been skipped.", symbol);
            if (acknowledgment != null) acknowledgment.acknowledge();
            return;
        }

        try {
            logger.info("Received Kafka message - topic: {}, partition: {}, offset: {}, rate: {}",
                    topic, partition, offset, rate);

            // 2) Filtreleme ve değer belirleme
            Rate rateToSave = applyOutlierFilter(rate, symbol);

            // 3) Database'e kaydet
            RateEntity entity = new RateEntity(
                    null,
                    symbol,
                    rateToSave.getAsk(),
                    rateToSave.getBid(),
                    LocalDateTime.parse(rate.getTimestamp()),
                    LocalDateTime.now()
            );

            rateRepository.save(entity);

            logger.info("Data saved to PostgreSQL - symbol: {}, ask: {}, bid: {}",
                    symbol, rateToSave.getAsk(), rateToSave.getBid());

            // 4) Acknowledge
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                logger.debug("Acknowledged offset {}", offset);
            }

        } catch (Exception ex) {
            logger.error("Error processing Kafka message - rate: {}, error: {}", rate, ex.getMessage(), ex);
            throw ex; // retry
        }
    }

    private Rate applyOutlierFilter(Rate newRate, String symbol) {
        double newAsk = newRate.getAsk();
        double newBid = newRate.getBid();
        double newMid = (newAsk + newBid) / 2.0;

        Rate lastValidRate = lastValidRateMap.get(symbol);

        // İlk kayıt durumu
        if (lastValidRate == null) {
            lastValidRateMap.put(symbol, newRate);
            consecutiveOutlierCount.put(symbol, 0);
            logger.debug("First record for {}: {}", symbol, newMid);
            return newRate; // Yeni Rate'i döndür
        }

        // Önceki geçerli değerlerle karşılaştır
        double lastValidAsk = lastValidRate.getAsk();
        double lastValidBid = lastValidRate.getBid();
        double lastValidMid = (lastValidAsk + lastValidBid) / 2.0;

        double percentageChange = Math.abs(newMid - lastValidMid) / lastValidMid;
        int currentOutlierCount = consecutiveOutlierCount.getOrDefault(symbol, 0);

        // Outlier kontrolü
        if (percentageChange > maxDiff) {
            // Çok fazla ardışık outlier varsa zorla kabul et
            if (currentOutlierCount >= MAX_CONSECUTIVE_OUTLIERS) {
                logger.warn("Forcing acceptance after {} consecutive outliers for {}: newMid={}, lastMid={}, change={}%",
                        MAX_CONSECUTIVE_OUTLIERS, symbol, newMid, lastValidMid, percentageChange * 100);

                lastValidRateMap.put(symbol, newRate);
                consecutiveOutlierCount.put(symbol, 0);
                return newRate; // Yeni Rate'i döndür
            } else {
                // Outlier: eski değeri kullan
                consecutiveOutlierCount.put(symbol, currentOutlierCount + 1);

                logger.warn("Outlier detected for {}: newMid={}, lastMid={}, change={}%. Using previous values. (Consecutive: {})",
                        symbol, newMid, lastValidMid, percentageChange * 100, currentOutlierCount + 1);

                return lastValidRate; // Eski Rate'i döndür
            }
        } else {
            // Kabul edilebilir değişim: yeni değeri kullan ve referansı güncelle
            lastValidRateMap.put(symbol, newRate);
            consecutiveOutlierCount.put(symbol, 0);

            logger.debug("Accepted new value for {}: newMid={}, change={}%",
                    symbol, newMid, percentageChange * 100);

            return newRate; // Yeni Rate'i döndür
        }
    }

}