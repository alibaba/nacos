package com.alibaba.nacos.client.config.listener.impl;

import com.alibaba.nacos.api.config.ConfigChangeEvent;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * AbstractConfigChangeListenerTest.
 *
 * @author shalk
 * @since 2021
 */
public class AbstractConfigChangeListenerTest {
    
    @Test
    public void receiveConfigInfo() {
        final Deque<String> data = new ArrayDeque<String>();
        AbstractConfigChangeListener a = new AbstractConfigChangeListener() {
            @Override
            public void receiveConfigChange(ConfigChangeEvent event) {
            }
            
            @Override
            public void receiveConfigInfo(String configInfo) {
                super.receiveConfigInfo(configInfo);
                data.offer(configInfo);
            }
        };
        a.receiveConfigInfo("foo");
        final String actual = data.poll();
        Assert.assertEquals("foo", actual);
    }
}