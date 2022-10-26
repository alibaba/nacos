package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionLimitRule;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * two fixed metrics, total 30, iptotal 15, detail is testa(total-20,iptotal-10),testb(total-10,iptotal-5).
 */
public class ConnectionControlManagerTest {
    
    ConnectionControlManager connectionControlManager = new ConnectionControlManager();
    
    @Test
    public void testPass() {
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(100);
        connectionLimitRule.setCountLimitPerClientIpDefault(100);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_PASS, check.getCheckCode());
        
    }
    
    
    @Test
    public void testPassByWhiteListLabel() {
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(1);
        connectionLimitRule.setCountLimitPerClientIpDefault(0);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.2", "test", "sdk");
        connectionCheckRequest.setAppName("diamond");
        connectionCheckRequest.setClientIp("127.0.0.2");
        connectionCheckRequest.setSource("cluster");
        Map<String, String> labels = new HashMap<>();
        labels.put("nolimitlabel", "Y");
        connectionCheckRequest.setLabels(labels);
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_PASS, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByTotalCount() {
        
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(30);
        connectionLimitRule.setCountLimitPerClientIpDefault(16);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_DENY, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByIpTotalCountDefault() {
        
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(15);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_DENY, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByIpTotalCountSpecific() {
        
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(16);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 15);
        connectionLimitRule.setCountLimitPerClientIp(ipConfig);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_DENY, check.getCheckCode());
        
    }
    
    @Test
    public void testPassByIpTotalCountSpecific() {
        
        CpuTestUtils.cpuOverLoad = false;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(15);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 16);
        connectionLimitRule.setCountLimitPerClientIp(ipConfig);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.CHECK_PASS, check.getCheckCode());
        
    }
    
    
    @Test
    public void testDeniedByCpuInterceptor() {
        
        CpuTestUtils.cpuOverLoad = true;
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(20);
        connectionLimitRule.setCountLimitPerClientIpDefault(15);
        connectionControlManager.setConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
    }
}
