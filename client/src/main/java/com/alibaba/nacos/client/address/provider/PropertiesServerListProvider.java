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

package com.alibaba.nacos.client.address.provider;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.StringTokenizer;

/**
 * Properties Server List Provider.
 *
 * @author misakacoder
 */
public class PropertiesServerListProvider implements ServerListProvider {
    
    public static final String NAME_PREFIX = "fixed";
    
    private String name;
    
    private List<String> serverList;
    
    @Override
    public void startup(NacosClientProperties properties, String namespace, ModuleType moduleType)
            throws NacosException {
        initServerList(properties);
        initName(namespace);
    }
    
    @Override
    public List<String> getServerList() throws NacosException {
        return serverList;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public int getOrder() {
        return 100;
    }
    
    @Override
    public boolean supportRefresh() {
        return false;
    }
    
    private void initName(String namespace) {
        this.name = String.join("-", NAME_PREFIX, namespace, getNameSuffix());
    }
    
    private void initServerList(NacosClientProperties properties) {
        List<String> serverList = new ArrayList<>();
        String serverAddress = properties.getProperty(PropertyKeyConst.SERVER_ADDR);
        if (StringUtils.isNotBlank(serverAddress)) {
            StringTokenizer tokenizer = new StringTokenizer(serverAddress, ",;");
            while (tokenizer.hasMoreTokens()) {
                serverList.add(tokenizer.nextToken());
            }
        }
        this.serverList = serverList;
    }
    
    private String getNameSuffix() {
        StringJoiner joiner = new StringJoiner("-");
        for (String server : serverList) {
            server = server.replaceAll("http(s)?://", "");
            joiner.add(server.replaceAll(":", "_"));
        }
        return joiner.toString();
    }
}
