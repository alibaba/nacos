/*
 *   Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.alibaba.nacos.client.serverlist.holder.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
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
    public static final String NAME = "fixed";

    private List<String> fixedServerList = new ArrayList<>();

    @Override
    public List<String> getServerList() {
        return fixedServerList;
    }

    @Override
    public boolean canApply(NacosClientProperties properties, String moduleName) {
        String serverListFromProps = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        if (StringUtils.isNotEmpty(serverListFromProps)) {
            this.fixedServerList = Arrays.asList(serverListFromProps.split(","));
            return true;
        }
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE;
    }
}
