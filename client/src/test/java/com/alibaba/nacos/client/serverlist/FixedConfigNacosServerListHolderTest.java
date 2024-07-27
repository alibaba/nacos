package com.alibaba.nacos.client.serverlist;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.impl.FixedConfigNacosServerListHolder;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

/**
 * fixed config get nacos server list.
 *
 * @author xz
 * @since 2024/7/25 16:06
 */
public class FixedConfigNacosServerListHolderTest {

    @Test
    public void testGetServerList() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();
        List<String> serverList = holder.getServerList();

        Assert.assertTrue(serverList.isEmpty());

    }

    @Test
    public void testInitServerList() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, "127.0.0.1:8848");

        List<String> serverList = holder.initServerList(NacosClientProperties.PROTOTYPE.derive(properties));
        Assert.assertEquals(1, serverList.size());
        Assert.assertEquals("127.0.0.1:8848", serverList.get(0));
    }

    @Test
    public void testTestGetName() {
        FixedConfigNacosServerListHolder holder = new FixedConfigNacosServerListHolder();

        Assert.assertEquals(holder.getName(), "fixed");
    }
}