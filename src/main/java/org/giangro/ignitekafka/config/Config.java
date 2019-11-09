/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.giangro.ignitekafka.config;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 *
 * @author Alex
 */

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties
@EnableScheduling
@ComponentScan("org.giangro.ignitekafka")
public class Config {
    
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
    
    @Value("${general.message}")
    private String message;

    public String getMessage() {
        return message;
    }
 
    @PostConstruct
    public void init(){        
    }
    
}
