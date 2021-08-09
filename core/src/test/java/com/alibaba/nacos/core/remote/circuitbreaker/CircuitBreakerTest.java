package com.alibaba.nacos.core.remote.circuitbreaker;

import org.junit.Assert;
import org.junit.Test;

public class CircuitBreakerTest {

    @Test
    public void testCircuitBreakerSPI() {
        Assert.assertTrue(CircuitBreaker.check("test1"));
    }

}
