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

package com.alibaba.nacos.maintainer.client.address;

import com.alibaba.nacos.maintainer.client.constants.PropertyKeyConstants;
import com.alibaba.nacos.maintainer.client.constants.RequestUrlConstants;
import com.alibaba.nacos.maintainer.client.env.NacosClientProperties;
import com.alibaba.nacos.maintainer.client.exception.NacosException;
import com.alibaba.nacos.maintainer.client.remote.client.NacosRestTemplate;
import com.alibaba.nacos.maintainer.client.utils.InternetAddressUtil;
import com.alibaba.nacos.maintainer.client.utils.ParamUtil;
import com.alibaba.nacos.maintainer.client.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Properties server list provider.
 * 
 * @author Nacos
 */
public class PropertiesListProvider extends AbstractServerListProvider {
    
    private static final String FIXED_NAME = "fixed";
    
    private List<String> serverList;
    
    @Override
    public void init(final NacosClientProperties properties, final NacosRestTemplate nacosRestTemplate) throws NacosException {
        super.init(properties, nacosRestTemplate);
        serverList = new ArrayList<>();
        String serverAddrsStr = properties.getProperty(PropertyKeyConstants.SERVER_ADDR);
        StringTokenizer serverAddrsTokens = new StringTokenizer(serverAddrsStr, ",;");
        while (serverAddrsTokens.hasMoreTokens()) {
            String serverAddr = serverAddrsTokens.nextToken().trim();
            if (serverAddr.startsWith(RequestUrlConstants.HTTP_PREFIX) || serverAddr.startsWith(RequestUrlConstants.HTTPS_PREFIX)) {
                this.serverList.add(serverAddr);
            } else {
                String[] serverAddrArr = InternetAddressUtil.splitIpPortStr(serverAddr);
                if (serverAddrArr.length == 1) {
                    this.serverList
                            .add(serverAddrArr[0] + InternetAddressUtil.IP_PORT_SPLITER + ParamUtil.getDefaultServerPort());
                } else {
                    this.serverList.add(serverAddr);
                }
            }
        }
    }
    
    @Override
    public List<String> getServerList() {
        return serverList;
    }
    
    @Override
    public String getServerName() {
        return FIXED_NAME + "-" + (StringUtils.isNotBlank(namespace) ? (StringUtils.trim(namespace) + "-")
                : "") + ParamUtil.getNameSuffixByServerIps(serverList.toArray(new String[0]));
    }
    
    @Override
    public int getOrder() {
        return PropertyKeyConstants.ADDRESS_SERVER_LIST_PROVIDER_ORDER;
    }
    
    @Override
    public boolean match(final NacosClientProperties properties) {
        return StringUtils.isNotBlank(properties.getProperty(PropertyKeyConstants.SERVER_ADDR));
    }
    
    @Override
    public boolean isFixed() {
        return true;
    }
    
    @Override
    public void shutdown() throws NacosException {
    }
}
