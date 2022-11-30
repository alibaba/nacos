package com.alibaba.nacos.plugin.control.connection;

import com.alibaba.nacos.plugin.control.connection.nacos.NacosConnectionControlManager;
import com.alibaba.nacos.plugin.control.connection.request.ConnectionCheckRequest;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckCode;
import com.alibaba.nacos.plugin.control.connection.response.ConnectionCheckResponse;
import com.alibaba.nacos.plugin.control.connection.rule.ConnectionControlRule;
import org.junit.Assert;
import org.junit.Test;

/**
 * two fixed metrics, total 30, iptotal 15, detail is testa(total-20,iptotal-10),testb(total-10,iptotal-5).
 */
public class NacosConnectionControlManagerTest {
    
    NacosConnectionControlManager connectionControlManager = new NacosConnectionControlManager();
    
    @Test
    public void test() {
        
        ConnectionControlRule connectionControlRule = new ConnectionControlRule();
        connectionControlRule.setCountLimit(30);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        ConnectionCheckRequest connectionCheckRequest = new ConnectionCheckRequest("127.0.0.1", "test", "sdk");
        ConnectionCheckResponse check = connectionControlManager.check(connectionCheckRequest);
        Assert.assertFalse(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.DENY_BY_TOTAL_OVER, check.getCode());
        
        connectionControlRule.setCountLimit(40);
        connectionControlManager.applyConnectionLimitRule(connectionControlRule);
        check = connectionControlManager.check(connectionCheckRequest);
        Assert.assertTrue(check.isSuccess());
        Assert.assertEquals(ConnectionCheckCode.PASS_BY_TOTAL, check.getCode());
        
    }
    
}
