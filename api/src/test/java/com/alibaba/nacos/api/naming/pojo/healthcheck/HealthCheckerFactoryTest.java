package com.alibaba.nacos.api.naming.pojo.healthcheck;

import static org.junit.Assert.*;

import org.junit.Test;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Tcp;

public class HealthCheckerFactoryTest {

    @Test
    public void testSerialize() {
        Tcp tcp = new Tcp();
        String actual = HealthCheckerFactory.serialize(tcp);
        assertTrue(actual.contains("\"type\":\"TCP\""));
    }

    @Test
    public void testSerializeExtend() {
        HealthCheckerFactory.registerSubType(TestChecker.class, TestChecker.TYPE);
        TestChecker testChecker = new TestChecker();
        String actual = HealthCheckerFactory.serialize(testChecker);
        assertTrue(actual.contains("\"type\":\"TEST\""));
    }

    @Test
    public void testDeserialize() {
        String tcpString = "{\"type\":\"TCP\"}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(Tcp.class, actual.getClass());
    }

    @Test
    public void testDeserializeExtend() {
        String tcpString = "{\"type\":\"TEST\",\"testValue\":null}";
        AbstractHealthChecker actual = HealthCheckerFactory.deserialize(tcpString);
        assertEquals(TestChecker.class, actual.getClass());
    }
}
