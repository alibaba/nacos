package com.alibaba.nacos.client.serverlist;

import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.impl.CompositeNacosServerListHolder;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

/**
 * composite server list holder test.
 *
 * @author xz
 * @since 2024/7/25 16:21
 */
public class CompositeNacosServerListHolderTest {

    @Test
    public void testLoadSpiServerListHolder() {
        CompositeNacosServerListHolder holder = new CompositeNacosServerListHolder();

        Properties properties = new Properties();
        List<String> initServerList = holder.initServerList(NacosClientProperties.PROTOTYPE.derive(properties));
        List<String> serverList = holder.getServerList();

        Assert.assertEquals(initServerList.size(), 1);
        Assert.assertEquals(serverList.size(), 1);
        Assert.assertEquals(serverList.get(0), "127.0.0.1:8848");
        Assert.assertEquals(initServerList.get(0), "127.0.0.1:8848");

        Assert.assertEquals(holder.getName(), "test");
    }

    @Test
    public void testGetServerList() {
        CompositeNacosServerListHolder holder = new CompositeNacosServerListHolder();

        List<String> serverList = holder.getServerList();
        Assert.assertEquals(serverList.size(), 0);
    }

    @Test
    public void testInitServerList() {
        CompositeNacosServerListHolder holder = new CompositeNacosServerListHolder();

        Properties properties = new Properties();

        List<String> serverList = holder.initServerList(NacosClientProperties.PROTOTYPE.derive(properties));

        Assert.assertEquals(serverList.size(), 0);
    }

    @Test
    public void testGetName() {
        CompositeNacosServerListHolder holder = new CompositeNacosServerListHolder();

        Assert.assertEquals(holder.getName(), "composite");
    }
}