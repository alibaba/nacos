package com.alibaba.nacos.client.spi;

import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.client.env.NacosClientProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * test nacos server list holder spi.
 *
 * @author xz
 * @since 2024/7/25 16:35
 */
public class TestNacosServerListHolderSpi implements NacosServerListHolder {

    private List<String> testServerList = new ArrayList<>();

    public TestNacosServerListHolderSpi() {
        testServerList.add("127.0.0.1:8848");
    }

    @Override
    public List<String> getServerList() {
        return this.testServerList;
    }

    @Override
    public List<String> initServerList(NacosClientProperties properties) {
        return this.testServerList;
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE;
    }
}
