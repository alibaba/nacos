package com.alibaba.nacos.plugin.control.connection.mse;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.ConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * two fixed metrics, total 30, iptotal 15, detail is testa(total-20,iptotal-10),testb(total-10,iptotal-5).
 */
public class MseConnectionControlManagerTest {
    
    ConnectionControlManager connectionControlManager = new MseConnectionControlManager();
    
    static {
        ControlConfigs.getInstance().setConnectionEnabled(true);
    }
    
    @Test
    public void testPass() {
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(100);
        connectionControlRule.setCountLimitPerClientIpDefault(100);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_TOTAL, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByTotalCount() {
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(16);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_TOTAL_OVER, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByIpTotalCountDefault() {
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(40);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_IP_OVER, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByIpTotalCountSpecific() {
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(40);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(16);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 15);
        connectionControlRule.setCountLimitPerClientIp(ipConfig);
        System.out.println(JacksonUtils.toJson(connectionControlRule));
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_IP_OVER, check.getCheckCode());
        
    }
    
    @Test
    public void testOverTurnedForIpTotalCountDefault() {
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(40);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        Map<String, String> labels = new HashMap<>();
        labels.put("overturned", "Y");
        connectionCheckRequest.setLabels(labels);
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_POST_INTERCEPT, check.getCheckCode());
        
    }
    
    @Test
    public void testPassByIpTotalCountSpecific() {
        
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(40);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 16);
        connectionControlRule.setCountLimitPerClientIp(ipConfig);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_IP, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByCpuInterceptor() {
        
        CpuTestUtils.cpuOverLoad = true;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(20);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(15);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        Assert.assertEquals(check.getCheckCode(), ConnectionCheckCode.DENY_BY_PRE_INTERCEPT);
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
    }
    
    @Test
    public void testPassByWhiteListLabel() {
        CpuTestUtils.cpuOverLoad = false;
        MseConnectionControlRule connectionControlRule = new MseConnectionControlRule();
        connectionControlRule.setCountLimit(1);
        connectionControlRule.setMonitorType(MonitorType.INTERCEPT.type);
        connectionControlRule.setCountLimitPerClientIpDefault(0);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.2", "test", "sdk");
        connectionCheckRequest.setAppName("diamond");
        connectionCheckRequest.setClientIp("127.0.0.2");
        connectionCheckRequest.setSource("cluster");
        Map<String, String> labels = new HashMap<>();
        labels.put("nolimitlabel", "Y");
        connectionCheckRequest.setLabels(labels);
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_PRE_INTERCEPT, check.getCheckCode());
        
    }
}
