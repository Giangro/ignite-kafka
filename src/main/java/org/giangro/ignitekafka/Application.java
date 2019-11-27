package org.giangro.ignitekafka;

import java.util.UUID;
import org.giangro.ignitekafka.config.Config;
import org.giangro.ignitekafka.service.IgniteKafkaMessageStore;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.messaging.support.MessageBuilder;

@SpringBootApplication
@EnableBinding(Processor.class)
public class Application {

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    @Autowired
    private Config config;
    
    @Autowired 
    private Processor channel;
    
    @Autowired 
    private IgniteKafkaMessageStore kafkaMessageStore;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @StreamListener(Processor.INPUT)
    public void handle(String id) {
        String message = kafkaMessageStore.get(id);
        logger.info("Received: \"" + message + "\" from kafka with id: " + id);
    }

    @Scheduled(fixedDelay = 1000, initialDelay = 10000)
    public void scheduleFixedRateWithInitialDelayTask() {
        //logger.info ("sending message to kafka...");
        
        String id = UUID.randomUUID().toString();
       
        kafkaMessageStore.put(id, config.getMessage());
        
        channel
                .output()
                .send(MessageBuilder.withPayload(id)
                        .build());
    }
        
}
