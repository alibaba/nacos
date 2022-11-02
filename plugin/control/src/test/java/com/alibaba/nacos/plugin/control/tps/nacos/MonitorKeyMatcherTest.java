package com.alibaba.nacos.plugin.control.tps.nacos;

import com.alibaba.nacos.plugin.control.tps.key.MonitorKeyMatcher;
import org.junit.Assert;
import org.junit.Test;

public class MonitorKeyMatcherTest {
    
    @Test
    public void testKeyMatcher() {
        Assert.assertTrue(MonitorKeyMatcher.match("test:abc*", "test:abc"));
        Assert.assertTrue(MonitorKeyMatcher.match("test:123*", "test:1234"));
        Assert.assertTrue(MonitorKeyMatcher.match("test:123*12", "test:12312"));
        Assert.assertTrue(MonitorKeyMatcher.match("test:*eee", "test:12312eee"));
        Assert.assertFalse(MonitorKeyMatcher.match("test:*eee", "test2:12312eee"));
        Assert.assertFalse(MonitorKeyMatcher.match("test:*eee", "test2:12312bee"));
        Assert.assertTrue(MonitorKeyMatcher.match("test:abc+eee", "test:abc+eee"));
        
    }
}
