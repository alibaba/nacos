package com.alibaba.nacos.client.config.listener.impl;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Properties;

/**
 * PropertiesListenerTest.
 *
 * @author shalk
 * @since 2021
 */
public class PropertiesListenerTest {
    
    @Test
    public void testReceiveConfigInfo() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo("foo=bar");
        final Properties actual = q2.poll();
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("bar", actual.getProperty("foo"));
        
    }
    
    @Test
    public void testReceiveConfigInfoEmpty() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo("");
        final Properties actual = q2.poll();
        Assert.assertNull(actual);
    }
    
    @Test
    public void testReceiveConfigInfoIsNotProperties() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        a.receiveConfigInfo(null);
        final Properties actual = q2.poll();
        Assert.assertNull(actual);
    }
    
    @Test
    public void testInnerReceive() {
        final Deque<Properties> q2 = new ArrayDeque<Properties>();
        PropertiesListener a = new PropertiesListener() {
            @Override
            public void innerReceive(Properties properties) {
                q2.offer(properties);
            }
        };
        Properties input = new Properties();
        input.put("foo", "bar");
        a.innerReceive(input);
        final Properties actual = q2.poll();
        Assert.assertEquals(1, actual.size());
        Assert.assertEquals("bar", actual.getProperty("foo"));
    }
    
}