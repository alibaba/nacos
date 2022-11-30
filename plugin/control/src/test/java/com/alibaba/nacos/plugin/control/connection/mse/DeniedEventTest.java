package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.notify.listener.Subscriber;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.event.mse.ConnectionDeniedEvent;
import com.alibaba.nacos.plugin.control.event.mse.TpsRequestDeniedEvent;
import com.alibaba.nacos.plugin.control.tps.MonitorType;
import com.alibaba.nacos.plugin.control.tps.TpsBarrier;
import com.alibaba.nacos.plugin.control.tps.mse.MseRuleDetail;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsBarrier;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsCheckRequest;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsControlRule;
import com.alibaba.nacos.plugin.control.tps.mse.MseTpsResultCode;
import com.alibaba.nacos.plugin.control.tps.response.TpsCheckResponse;
import com.alibaba.nacos.plugin.control.tps.response.TpsResultCode;
import com.alibaba.nacos.plugin.control.tps.rule.RuleModel;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * two fixed metrics, total 30, iptotal 15, detail is testa(total-20,iptotal-10),testb(total-10,iptotal-5).
 */
public class DeniedEventTest {
    
    @Test
    public void testTotalOverDenied() throws InterruptedException {
        String testTpsBarrier = "test_barrier" + System.currentTimeMillis();
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<ConnectionDeniedEvent> subscriber = new Subscriber<ConnectionDeniedEvent>() {
            @Override
            public void onEvent(ConnectionDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                Assert.assertFalse(event.isMonitorModel());
                deniedEventCount.incrementAndGet();
                
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConnectionDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.getType());
        connectionControlRule.setCountLimitPerClientIpDefault(16);
        ConnectionControlManager connectionControlManager = new MseConnectionControlManager();
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        for (int i = 0; i < 10; i++) {
            ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
            System.out.println(check.getCode());
            System.out.println(check.getMessage());
            
            Assert.assertFalse(check.isSuccess());
            Assert.assertEquals(ConnectionCheckCode.DENY_BY_TOTAL_OVER, check.getCode());
        }
        
        Thread.sleep(1000L);
        //check event
        Assert.assertEquals(10, deniedEventCount.get());
        
    }
    
    @Test
    public void testTotalOverDeniedMonitor() throws InterruptedException {
        
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<ConnectionDeniedEvent> subscriber = new Subscriber<ConnectionDeniedEvent>() {
            @Override
            public void onEvent(ConnectionDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                Assert.assertTrue(event.isMonitorModel());
                deniedEventCount.incrementAndGet();
                
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConnectionDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        connectionControlRule.setMonitorType(MonitorType.MONITOR.getType());
        connectionControlRule.setCountLimitPerClientIpDefault(16);
        ConnectionControlManager connectionControlManager = new MseConnectionControlManager();
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        for (int i = 0; i < 10; i++) {
            ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
            System.out.println(check.getCode());
            System.out.println(check.getMessage());
            
            Assert.assertTrue(check.isSuccess());
            Assert.assertEquals(MseConnectionCheckCode.PASS_BY_MONITOR, check.getCode());
        }
        
        Thread.sleep(1000L);
        //check event
        Assert.assertEquals(10, deniedEventCount.get());
        
    }
    
    @Test
    public void testIpOverOverLimit() throws InterruptedException {
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<ConnectionDeniedEvent> subscriber = new Subscriber<ConnectionDeniedEvent>() {
            @Override
            public void onEvent(ConnectionDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                Assert.assertFalse(event.isMonitorModel());
                deniedEventCount.incrementAndGet();
                
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConnectionDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(1000);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        connectionControlRule
                .setMonitorType(com.alibaba.nacos.plugin.control.connection.mse.MonitorType.INTERCEPT.type);
        ConnectionControlManager connectionControlManager = new MseConnectionControlManager();
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
    
        for (int i = 0; i < 10; i++) {
            ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
            System.out.println(check.getCode());
            System.out.println(check.getMessage());
        
            Assert.assertFalse(check.isSuccess());
            Assert.assertEquals(MseConnectionCheckCode.DENY_BY_IP_OVER, check.getCode());
        }
        
        Thread.sleep(500L);
        //check event
        Assert.assertEquals(10, deniedEventCount.get());
        
    }
    @Test
    public void testIpOverOverLimitMonitor() throws InterruptedException {
        AtomicInteger deniedEventCount = new AtomicInteger();
        Subscriber<ConnectionDeniedEvent> subscriber = new Subscriber<ConnectionDeniedEvent>() {
            @Override
            public void onEvent(ConnectionDeniedEvent event) {
                System.out.println("event receive-" + event.getMessage());
                Assert.assertTrue(event.isMonitorModel());
                deniedEventCount.incrementAndGet();
                
            }
            
            @Override
            public Class<? extends Event> subscribeType() {
                return ConnectionDeniedEvent.class;
            }
        };
        
        NotifyCenter.registerSubscriber(subscriber);
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(1000);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        connectionControlRule
                .setMonitorType(com.alibaba.nacos.plugin.control.connection.mse.MonitorType.MONITOR.type);
        ConnectionControlManager connectionControlManager = new MseConnectionControlManager();
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        for (int i = 0; i < 10; i++) {
            ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
            System.out.println(check.getCode());
            System.out.println(check.getMessage());
            
            Assert.assertTrue(check.isSuccess());
            Assert.assertEquals(MseConnectionCheckCode.PASS_BY_MONITOR, check.getCode());
        }
        
        Thread.sleep(500L);
        //check event
        Assert.assertEquals(10, deniedEventCount.get());
        
    }
}
