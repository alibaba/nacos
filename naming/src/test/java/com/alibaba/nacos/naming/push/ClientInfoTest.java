package com.alibaba.nacos.naming.push;

import com.alibaba.nacos.naming.BaseTest;
import org.junit.Assert;
import org.junit.Test;


public class ClientInfoTest  extends BaseTest {
    
    @Test
    public void testClientInfo() {
        ClientInfo clientInfo = new ClientInfo("vip-client4cpp");
    
        Assert.assertEquals(ClientInfo.ClientType.C, clientInfo.type);
    }
}