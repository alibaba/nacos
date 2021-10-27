package com.alibaba.nacos.config.server.constant;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;

public class CounterModeTest {
    
    @Mock
    CounterMode counterMode;
    
    @Test
    public void testReverse() {
        counterMode = CounterMode.INCREMENT;
        Assert.assertEquals(CounterMode.DECREMENT, counterMode.reverse());
        counterMode = CounterMode.DECREMENT;
        Assert.assertEquals(CounterMode.INCREMENT, counterMode.reverse());
    }
    
}
