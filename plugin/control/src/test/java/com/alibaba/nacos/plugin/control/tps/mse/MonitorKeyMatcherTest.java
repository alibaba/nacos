package com.alibaba.nacos.plugin.control.tps.mse;

import com.alibaba.nacos.plugin.control.tps.mse.key.MatchType;
import com.alibaba.nacos.plugin.control.tps.mse.key.MonitorKeyMatcher;
import org.junit.Assert;
import org.junit.Test;

public class MonitorKeyMatcherTest {
    
    @Test
    public void testKeyMatcher() {
        Assert.assertEquals(MonitorKeyMatcher.parse("test:abc*", "test:abc"), MatchType.PREFIX);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:123*", "test:1234"), MatchType.PREFIX);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:123*12", "test:12312"), MatchType.PRE_POSTFIX);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:*eee", "test:12312eee"), MatchType.POSTFIX);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:*eee", "test2:12312eee"), MatchType.NO_MATCH);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:*eee", "test2:12312bee"), MatchType.NO_MATCH);
        Assert.assertEquals(MonitorKeyMatcher.parse("test:abc+eee", "test:abc+eee"), MatchType.EXACT);
        
    }
}
