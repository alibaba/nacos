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
import com.alibaba.nacos.client.constant.Constants;
import com.alibaba.nacos.client.serverlist.holder.NacosServerListHolder;
import com.alibaba.nacos.client.serverlist.utils.HttpUtil;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.InitUtils;
import com.alibaba.nacos.client.utils.ContextPathUtil;
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

    private final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();

    private List<String> initServerList = new ArrayList<>();

    private String namespace;

    private String endpoint;

    private String moduleName;

    private String contentPath = ParamUtil.getDefaultContextPath();

    private String serverListName = ParamUtil.getDefaultNodesPath();

    @Override
    public List<String> getServerList() {
        getServerListFromEndpoint();
        return initServerList;
    }

    @Override
    public List<String> initServerList(NacosClientProperties properties) {
        this.endpoint = InitUtils.initEndpoint(properties);
        if (StringUtils.isNotEmpty(endpoint)) {
            this.namespace = properties.getProperty(PropertyKeyConst.NAMESPACE);
            this.moduleName = properties.getProperty(PropertyKeyConst.MODULE_NAME);
            String contentPathTmp = properties.getProperty(PropertyKeyConst.CONTEXT_PATH);
            if (!StringUtils.isBlank(contentPathTmp)) {
                this.contentPath = contentPathTmp;
            }
            String serverListNameTmp = properties.getProperty(PropertyKeyConst.CLUSTER_NAME);
            if (!StringUtils.isBlank(serverListNameTmp)) {
                this.serverListName = serverListNameTmp;
            }

            return getServerListFromEndpoint();
        }
        return new ArrayList<>();
    }

    private List<String> getServerListFromEndpoint() {
        if (StringUtils.isBlank(endpoint)) {
            return new ArrayList<>();
        }
        try {
            StringBuilder addressServerUrlTem = new StringBuilder(
                    String.format("http://%s%s/%s", endpoint,
                            ContextPathUtil.normalizeContextPath(contentPath), serverListName));
            String urlString = addressServerUrlTem.toString();
            Header header = HttpUtil.builderHeaderByModule(moduleName);
            Query query = StringUtils.isNotBlank(namespace)
                    ? Query.newInstance().addParam("namespace", namespace)
                    : Query.EMPTY;
            HttpRestResult<String> restResult = nacosRestTemplate.get(urlString, header, query, String.class);
            if (!restResult.ok()) {
                throw new IOException(
                        "Error while requesting: " + urlString + "'. Server returned: " + restResult.getCode());
            }
            String content = restResult.getData();
            List<String> list = new ArrayList<>();
            for (String line : IoUtils.readLines(new StringReader(content))) {
                if (!line.trim().isEmpty()) {
                    list.add(line.trim());
                }
            }
            this.initServerList = list;
            return list;
        } catch (Exception e) {
            NAMING_LOGGER.error("[SERVER-LIST] failed to get server list.", e);
        }
        return new ArrayList<>();
    }

    @Override
    public String getName() {
        return Constants.ENDPOINT_NAME;
    }

    @Override
    public int getOrder() {
        return Integer.MAX_VALUE - 1;
    }
}
