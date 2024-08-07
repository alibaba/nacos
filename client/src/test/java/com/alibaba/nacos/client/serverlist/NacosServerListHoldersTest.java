package com.alibaba.nacos.client.serverlist;

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolders;
import com.alibaba.nacos.client.spi.TestNacosServerListHolderSpi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * composite server list holder test.
 *
 * @author xz
 * @since 2024/7/25 16:21
 */
public class NacosServerListHoldersTest {
    
    @Test
    public void testLoadSpiServerListHolder() {
        Properties properties = new Properties();
        TestNacosServerListHolderSpi.testEnable = true;
        NacosServerListHolders holder = new NacosServerListHolders(NacosClientProperties.PROTOTYPE.derive(properties), "test");
        List<String> serverList = holder.loadServerList();
        TestNacosServerListHolderSpi.testEnable = false;
        assertEquals(serverList.size(), 1);
        assertEquals(serverList.get(0), "127.0.0.1:8848");
        assertEquals(holder.getName(), "test");
    }
    
    @Test
    public void testGetServerList() {
        NacosServerListHolders holder = new NacosServerListHolders(NacosClientProperties.PROTOTYPE.derive(new Properties()), "test");
        
        List<String> serverList = holder.getServerList();
        assertEquals(serverList.size(), 0);
    }
    
    @Test
    public void testInitServerList() {
        Properties properties = new Properties();
        NacosServerListHolders holder = new NacosServerListHolders(NacosClientProperties.PROTOTYPE.derive(properties), "test");
        
        List<String> serverList = holder.getServerList();
        
        assertEquals(serverList.size(), 0);
    }
    
    @Test
    public void testGetName() {
        NacosServerListHolders holder = new NacosServerListHolders(NacosClientProperties.PROTOTYPE.derive(new Properties()), "test");
        
        assertEquals(holder.getName(), "serverListHolders");
    }
}