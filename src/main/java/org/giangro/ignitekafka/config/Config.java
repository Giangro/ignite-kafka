/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.giangro.ignitekafka.config;

import java.util.Arrays;
import java.util.Collection;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.binder.kafka.KafkaBindingRebalanceListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    @Value("${enableFilePersistence}")
    private boolean enableFilePersistence;
    @Value("${igniteConnectorPort}")
    private int igniteConnectorPort;
    @Value("${igniteServerPortRange}")
    private String igniteServerPortRange;
    @Value("${ignitePersistenceFilePath}")
    private String ignitePersistenceFilePath;
    @Value("${cacheName}")
    private String cacheName;

    private static final String DATA_CONFIG_NAME = "MyDataRegionConfiguration";

    @Bean
    IgniteConfiguration igniteConfiguration() {
        IgniteConfiguration igniteConfiguration = new IgniteConfiguration();
        igniteConfiguration.setWorkDirectory(ignitePersistenceFilePath);
        igniteConfiguration.setClientMode(false);
        // durable file memory persistence
        if (enableFilePersistence) {

            DataStorageConfiguration dataStorageConfiguration = new DataStorageConfiguration();
            dataStorageConfiguration.setStoragePath(ignitePersistenceFilePath + "/store");
            dataStorageConfiguration.setWalArchivePath(ignitePersistenceFilePath + "/walArchive");
            dataStorageConfiguration.setWalPath(ignitePersistenceFilePath + "/walStore");
            dataStorageConfiguration.setPageSize(4 * 1024);
            DataRegionConfiguration dataRegionConfiguration = new DataRegionConfiguration();
            dataRegionConfiguration.setName(DATA_CONFIG_NAME);
            dataRegionConfiguration.setInitialSize(100 * 1000 * 1000);
            dataRegionConfiguration.setMaxSize(200 * 1000 * 1000);
            dataRegionConfiguration.setPersistenceEnabled(true);
            dataStorageConfiguration.setDataRegionConfigurations(dataRegionConfiguration);
            igniteConfiguration.setDataStorageConfiguration(dataStorageConfiguration);
            igniteConfiguration.setConsistentId("IgniteKafkaFileSystem");
        }
        // connector configuration
        ConnectorConfiguration connectorConfiguration = new ConnectorConfiguration();
        connectorConfiguration.setPort(igniteConnectorPort);
        // common ignite configuration
        igniteConfiguration.setMetricsLogFrequency(0);
        igniteConfiguration.setQueryThreadPoolSize(2);
        igniteConfiguration.setDataStreamerThreadPoolSize(1);
        igniteConfiguration.setManagementThreadPoolSize(2);
        igniteConfiguration.setPublicThreadPoolSize(2);
        igniteConfiguration.setSystemThreadPoolSize(2);
        igniteConfiguration.setRebalanceThreadPoolSize(1);
        igniteConfiguration.setAsyncCallbackPoolSize(2);
        igniteConfiguration.setPeerClassLoadingEnabled(false);
        igniteConfiguration.setIgniteInstanceName("igniteKafkaGrid");
        BinaryConfiguration binaryConfiguration = new BinaryConfiguration();
        binaryConfiguration.setCompactFooter(false);
        igniteConfiguration.setBinaryConfiguration(binaryConfiguration);
        // cluster tcp configuration
        TcpDiscoverySpi tcpDiscoverySpi = new TcpDiscoverySpi();
        TcpDiscoveryVmIpFinder tcpDiscoveryVmIpFinder = new TcpDiscoveryVmIpFinder();
        // need to be changed when it come to real cluster
        tcpDiscoveryVmIpFinder.setAddresses(Arrays.asList("127.0.0.1:47500..47509"));
        tcpDiscoverySpi.setIpFinder(tcpDiscoveryVmIpFinder);
        igniteConfiguration.setLocalHost("127.0.0.1");
        igniteConfiguration.setDiscoverySpi(tcpDiscoverySpi);

        // cache configuration
        CacheConfiguration kafkamessages = new CacheConfiguration();
        kafkamessages.setCopyOnRead(false);
        // as we have one node for now
        kafkamessages.setBackups(1);
        kafkamessages.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        kafkamessages.setName(cacheName);
        kafkamessages.setDataRegionName(DATA_CONFIG_NAME);
        kafkamessages.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_ASYNC);
        kafkamessages.setIndexedTypes(String.class, String.class);

        igniteConfiguration.setCacheConfiguration(kafkamessages);

        return igniteConfiguration;

    }

    @Bean(destroyMethod = "close")
    Ignite ignite(IgniteConfiguration igniteConfiguration) throws IgniteException {
        final Ignite start = Ignition.start(igniteConfiguration);
        start.cluster().active(true);
        return start;
    }

    @Bean
    KafkaBindingRebalanceListener kafkaBindingRebalanceListener() {
        return new KafkaBindingRebalanceListener() {           
            @Override
            public void onPartitionsRevokedBeforeCommit(String bindingName, Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
                KafkaBindingRebalanceListener.super.onPartitionsRevokedBeforeCommit(bindingName, consumer, partitions); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void onPartitionsRevokedAfterCommit(String bindingName, Consumer<?, ?> consumer, Collection<TopicPartition> partitions) {
                KafkaBindingRebalanceListener.super.onPartitionsRevokedAfterCommit(bindingName, consumer, partitions); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void onPartitionsAssigned(String bindingName, Consumer<?, ?> consumer, Collection<TopicPartition> partitions, boolean initial) {
                logger.info("partition assigned = "+partitions.toString());
                KafkaBindingRebalanceListener.super.onPartitionsAssigned(bindingName, consumer, partitions, initial); //To change body of generated methods, choose Tools | Templates.
            }
    
        };
    }
    
    public String getMessage() {
        return message;
    }
    
    public String getCacheName () {
        return cacheName;
    }

    @PostConstruct
    public void init() {
    }

}
