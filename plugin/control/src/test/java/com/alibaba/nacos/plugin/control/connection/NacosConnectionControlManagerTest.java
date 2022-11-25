package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.plugin.control.configs.ControlConfigs;
import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
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
public class NacosConnectionControlManagerTest {
    
    NacosConnectionControlManager connectionControlManager = new NacosConnectionControlManager();
    static {
        ControlConfigs.getInstance().setConnectionEnabled(true);
    }
    @Test
    public void testPass() {
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(100);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_TOTAL, check.getCheckCode());
        
    }
    
    @Test
    public void testDeniedByTotalCount() {
        
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(30);
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
        
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(40);
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
        
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(40);
       
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
    public void testPassByIpTotalCountSpecific() {
    
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(40);
        
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
    
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.3", "test", "sdk");
    
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
    
        System.out.println(check.getCheckCode());
        System.out.println(check.getMessage());
    
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_IP, check.getCheckCode());
    
    }
    
}
