package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
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
public class NacosConnectionControlManagerTest {
    
    NacosConnectionControlManager connectionControlManager = new NacosConnectionControlManager();
    static {
        ControlConfigs.getInstance().setConnectionEnabled(true);
    }
    @Test
    public void testPass() {
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(100);
        connectionLimitRule.setCountLimitPerClientIpDefault(100);
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_TOTAL, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByTotalCount() {
        
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(30);
        connectionLimitRule.setCountLimitPerClientIpDefault(16);
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_TOTAL_OVER, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByIpTotalCountDefault() {
        
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(15);
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_IP_OVER, check.getCheckCode());
        
    }

    @Test
    public void testDeniedByIpTotalCountSpecific() {
        
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(16);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 15);
        connectionLimitRule.setCountLimitPerClientIp(ipConfig);
        System.out.println(JacksonUtils.toJson(connectionLimitRule));
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
        
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_IP_OVER, check.getCheckCode());
        
    }
    
    @Test
    public void testPassByIpTotalCountSpecific() {
    
        ConnectionLimitRule connectionLimitRule = new ConnectionLimitRule();
        connectionLimitRule.setCountLimit(40);
        connectionLimitRule.setCountLimitPerClientIpDefault(15);
        Map<String, Integer> ipConfig = new HashMap<>();
        ipConfig.put("127.0.0.3", 16);
        connectionLimitRule.setCountLimitPerClientIp(ipConfig);
        connectionControlManager.applyConnectionLimitRule(connectionLimitRule);
    
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
    
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
    
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
    
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_IP, check.getCheckCode());
    
    }
    
}
