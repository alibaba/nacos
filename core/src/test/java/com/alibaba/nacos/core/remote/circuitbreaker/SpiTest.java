package com.alibaba.nacos.core.remote.circuitbreaker;

import com.alibaba.nacos.core.remote.circuitbreaker.rule.flow.FlowControlConfig;
import com.alibaba.nacos.core.remote.control.ClientIpMonitorKey;
import com.alibaba.nacos.core.remote.control.ConnectionIdMonitorKey;
import com.alibaba.nacos.core.remote.control.MonitorKey;
import org.assertj.core.util.Lists;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class SpiTest {
    static CircuitBreaker circuitBreaker;

    @BeforeClass
    public static void setUpBeforeClass() throws InterruptedException {
        circuitBreaker = new CircuitBreaker("flowControl");
        circuitBreaker.registerPoint("test1");
        TimeUnit.SECONDS.sleep(1);
        Map<String, CircuitBreakerConfig> monitorKeyMap = new HashMap<>();
        monitorKeyMap.put("testKey:a*b", new FlowControlConfig(500, TimeUnit.SECONDS, "EACH", "intercept"));
        monitorKeyMap.put("testKey:*", new FlowControlConfig(2000000, TimeUnit.SECONDS, "SUM", "intercept"));
        FlowControlConfig pointConfig = new FlowControlConfig(800, TimeUnit.SECONDS, "SUM", "intercept");

        circuitBreaker.applyRule("test1", pointConfig, monitorKeyMap);
    }

    @Test
    public void testCircuitBreakerMonitorKeys() {
        List<MonitorKey> monitorKeyList = new ArrayList<>();
        monitorKeyList.add(new ClientIpMonitorKey("ab"));
        monitorKeyList.add(new ClientIpMonitorKey("at"));
        monitorKeyList.add(new ConnectionIdMonitorKey("connection1"));
        Assert.assertTrue(circuitBreaker.applyStrategyWithLoad("test1", monitorKeyList, 500));
    }

    @Test
    public void testCircuitBreakerTps() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 100; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";

            boolean pass = circuitBreaker.applyStrategyWithLoad("test1",
                    Lists.list(connectionId, new SpiTest.TestKey(value)), 4);
            assertTrue(pass);
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    public void testMonitorKeyTpsWithOverFlow() {
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 2000; i++) {
            String value = "atg" + (new Random().nextInt(100) + 2) + "efb";
            boolean pass = circuitBreaker.applyStrategyWithLoad("test1", Lists.list(connectionId, new SpiTest.TestKey(value)), 128);
            if (!pass) {
                return;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Assert.fail("fail to limit.");
    }

    @Test
    public void testTotalTpsWithOverFlow() {
        ConnectionIdMonitorKey connectionId = new ConnectionIdMonitorKey("connection1");
        for (int i = 0; i < 1000; i++) {
            boolean pass = circuitBreaker.applyStrategyWithLoad("test1", Lists.list(connectionId), 16);
            pass &= circuitBreaker.applyStrategyWithLoad("test1", Lists.list(connectionId), 16);
            if (!pass) {
                return;
            }
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Assert.fail("fail to limit.");
    }

    class TestKey extends MonitorKey {

        public TestKey(String key) {
            setKey(key);
        }

        @Override
        public String getType() {
            return "testKey";
        }
    }

}
