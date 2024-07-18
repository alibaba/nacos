/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.client.address.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.address.base.Order;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * Properties Server List Manager.
 *
 * @author misakacoder
 */
@Order(100)
public class PropertiesServerListManager extends AbstractServerListManager {

    private static final String NAME_PREFIX = "prop";

    public PropertiesServerListManager(NacosClientProperties properties) throws NacosException {
        super(properties);
    }

    @Override
    protected String initServerName(NacosClientProperties properties) {
        List<String> serverList = initServerList(properties);
        updateServerList(serverList);
        return NAME_PREFIX + "-" + getServerName();
    }

    private List<String> initServerList(NacosClientProperties properties) {
        List<String> serverList = new ArrayList<>();
        String serverAddress = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        if (StringUtils.isNotBlank(serverAddress)) {
            StringTokenizer tokenizer = new StringTokenizer(serverAddress, ",;");
            while (tokenizer.hasMoreTokens()) {
                serverList.add(tokenizer.nextToken());
            }
        }
        return serverList;
    }

    private String getServerName() {
        StringJoiner joiner = new StringJoiner("-");
        for (String server : getServerList()) {
            server = server.replaceAll("http(s)?://", "");
            joiner.add(server.replaceAll(":", "_"));
        }
        return joiner.toString();
    }
}
