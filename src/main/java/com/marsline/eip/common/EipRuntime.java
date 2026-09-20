package com.marsline.eip.common;

import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One isolated integration runtime per task: an embedded ActiveMQ broker plus its own CamelContext
 * with the "jms" component wired to that broker. Closing it stops Camel first, then the broker.
 */
public final class EipRuntime implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(EipRuntime.class);

    private final BrokerSupport broker;
    private final CamelContext context;

    private EipRuntime(BrokerSupport broker, CamelContext context) {
        this.broker = broker;
        this.context = context;
    }

    public static EipRuntime start(String contextName) throws Exception {
        BrokerSupport broker = BrokerSupport.start();
        try {
            DefaultCamelContext ctx = new DefaultCamelContext();
            ctx.setName(contextName);
            JmsComponent jms = new JmsComponent();
            jms.setConnectionFactory(BrokerSupport.connectionFactory());
            ctx.addComponent("jms", jms);
            return new EipRuntime(broker, ctx);
        } catch (Exception e) {
            broker.stop();
            throw e;
        }
    }

    public CamelContext context() {
        return context;
    }

    public void startCamel() throws Exception {
        context.start();
    }

    @Override
    public void close() {
        try {
            LOG.info("Stopping Apache Camel ...");
            context.stop();
        } catch (Exception e) {
            LOG.warn("Camel did not stop cleanly: {}", e.getMessage());
        } finally {
            broker.stop();
        }
    }
}
