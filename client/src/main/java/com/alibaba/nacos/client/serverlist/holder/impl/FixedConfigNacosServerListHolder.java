package com.alibaba.nacos.client.serverlist.holder.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * use fixed property to get serverList.
 *
 * @author xz
 * @since 2024/7/24 16:28
 */
public class FixedConfigNacosServerListHolder implements NacosServerListHolder {

    private List<String> fixedServerList = new ArrayList<>();

    @Override
    public List<String> getServerList() {
        return fixedServerList;
    }

    @Override
    public List<String> initServerList(NacosClientProperties properties) {
        String serverListFromProps = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        if (StringUtils.isNotEmpty(serverListFromProps)) {
            this.fixedServerList = Arrays.asList(serverListFromProps.split(","));
            return fixedServerList;
        }
        return new ArrayList<>();
    }

    @Override
    public String getName() {
        return Constants.FIXED_NAME;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }

}
