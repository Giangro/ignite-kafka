/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.giangro.ignitekafka.service;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.giangro.ignitekafka.config.Config;

/**
 *
 * @author GIANGR40
 */
@Service
public class IgniteKafkaMessageStore {

    @Autowired 
    private Config config;
    
    @Autowired
    private Ignite ignite;

    public String get(String id) {
        return this.getKafkaMessageCache().get(id);
    }
    
    public Boolean put(String id, String message) {
        return this.getKafkaMessageCache().putIfAbsent(id, message);
    }
    
    protected IgniteCache<String, String> getKafkaMessageCache() {
        return ignite.cache(config.getCacheName());
    }

}
