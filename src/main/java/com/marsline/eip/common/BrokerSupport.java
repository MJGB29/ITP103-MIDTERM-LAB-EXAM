package com.marsline.eip.common;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Starts / stops the real, embedded, in-JVM, non-persistent Apache ActiveMQ Classic broker. */
public final class BrokerSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrokerSupport.class);

    public static final String BROKER_NAME = "marsline-broker";
    public static final String VM_URL = "vm://" + BROKER_NAME;

    private final BrokerService broker;

    private BrokerSupport(BrokerService broker) {
        this.broker = broker;
    }

    public static BrokerSupport start() throws Exception {
        LOG.info("Starting embedded ActiveMQ broker ({}) ...", VM_URL);
        BrokerService broker = new BrokerService();
        broker.setBrokerName(BROKER_NAME);
        broker.setPersistent(false);
        broker.setUseJmx(false);
        broker.setSchedulerSupport(false);
        broker.setUseShutdownHook(false);
        broker.addConnector(VM_URL);
        broker.start();
        broker.waitUntilStarted();
        LOG.info("Embedded ActiveMQ broker started: {}", BROKER_NAME);
        return new BrokerSupport(broker);
    }

    /** JMS connection factory that attaches to the already running embedded broker. */
    public static ConnectionFactory connectionFactory() {
        return new ActiveMQConnectionFactory(VM_URL + "?create=false");
    }

    public void stop() {
        try {
            LOG.info("Stopping embedded ActiveMQ broker ...");
            broker.stop();
            broker.waitUntilStopped();
            LOG.info("Embedded ActiveMQ broker stopped");
        } catch (Exception e) {
            LOG.warn("Broker did not stop cleanly: {}", e.getMessage());
        }
    }
}
