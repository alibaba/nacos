/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.serverlist.holder.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.client.serverlist.utils.HttpUtil;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static com.alibaba.nacos.client.utils.LogUtils.NAMING_LOGGER;

/**
 * by endpoint get nacos server list.
 *
 * @author xz
 * @since 2024/7/24 16:56
 */
public class EndpointNacosServerListHolder implements NacosServerListHolder {
    public static final String NAME = "endpoint";

    private final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();


    private String endpoint;

    private String endpointUrlString;

    private Header queryHeader;

    @Override
    public List<String> getServerList() {
        return doGetServerList();
    }

    @Override
    public boolean canApply(NacosClientProperties properties) {
        String endpoint = InitUtils.initEndpoint(properties);
        if (StringUtils.isNotEmpty(endpoint)) {
            this.endpoint = endpoint;
            initRequestInfo(properties);
            return true;
        }
        return false;
    }

    private void initRequestInfo(NacosClientProperties properties) {
        final String namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
        final String contextPath = InitUtils.initContextPath(properties);

        final String moduleName = properties.getProperty(PropertyKeyConst.MODULE_NAME);
        String serverListName = ParamUtil.getDefaultNodesPath();

        String serverListNameTmp = properties.getProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME,
                properties.getProperty(PropertyKeyConst.CLUSTER_NAME));
        if (!StringUtils.isBlank(serverListNameTmp)) {
            serverListName = serverListNameTmp;
        }

        StringBuilder urlString = new StringBuilder(
                String.format("http://%s%s/%s", this.endpoint, contextPath, serverListName));
        boolean hasQueryString = false;
        if (StringUtils.isNotBlank(namespace)) {
            urlString.append("?namespace=").append(namespace);
            hasQueryString = true;
        }
        if (properties.containsKey(PropertyKeyConst.ENDPOINT_QUERY_PARAMS)) {
            urlString.append(
                    hasQueryString ? "&" : "?" + properties.getProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS));
        }
        if (Constants.Naming.NAMING_MODULE.equals(moduleName)) {
            this.queryHeader = HttpUtil.buildNamingHeader();
        } else {
            this.queryHeader = HttpUtil.buildHeaderByModule(moduleName);
        }

        this.endpointUrlString = urlString.toString();
    }

    private List<String> doGetServerList() {
        if (StringUtils.isBlank(endpoint)) {
            return new ArrayList<>();
        }
        try {
            HttpRestResult<String> restResult = nacosRestTemplate.get(endpointUrlString, queryHeader, Query.EMPTY, String.class);
            if (!restResult.ok()) {
                throw new IOException(
                        "Error while requesting: " + endpointUrlString + "'. Server returned: " + restResult.getCode());
            }
            String content = restResult.getData();
            List<String> list = new ArrayList<>();
            for (String line : IoUtils.readLines(new StringReader(content))) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }

            return list;
        } catch (Exception e) {
            NAMING_LOGGER.error("[SERVER-LIST] failed to get server list.", e);
        }

        return new ArrayList<>();
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }
}
