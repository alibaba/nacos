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
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.common.ModuleType;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.naming.utils.NamingHttpUtil;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.IoUtils;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Address Server List Provider.
 *
 * @author misakacoder
 */
public class AddressServerListProvider implements ServerListProvider {
    
    private static final int DEFAULT_ENDPOINT_PORT = 8080;
    
    private static final int DEFAULT_ENDPOINT_RETRY_TIME = 3;
    
    public static final String NAME_PREFIX = "custom";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(AddressServerListProvider.class);
    
    private String name;
    
    private String namespace;
    
    private String endpoint;
    
    private int endpointPort;
    
    private int endpointRetryTime;
    
    private String endpointContextPath;
    
    private String endpointClusterName;
    
    private String addressServerUrl;
    
    private Header header = Header.EMPTY;
    
    private Query query = Query.EMPTY;
    
    private NacosRestTemplate nacosRestTemplate;
    
    @Override
    public void startup(NacosClientProperties properties, String namespace, ModuleType moduleType)
            throws NacosException {
        this.namespace = namespace;
        initEndpoint(properties);
        initEndpointPort(properties);
        initEndpointRetryTime(properties);
        initEndpointContextPath(properties);
        initEndpointClusterName(properties);
        initAddressServerUrl(properties, moduleType);
        initName();
        this.nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
        if (moduleType == ModuleType.NAMING) {
            header = NamingHttpUtil.builderHeader();
            if (StringUtils.isNotBlank(namespace)) {
                query = Query.newInstance().addParam("namespace", namespace);
            }
        }
    }
    
    @Override
    public boolean isValid(NacosClientProperties properties) {
        initEndpoint(properties);
        return StringUtils.isNotBlank(endpoint);
    }
    
    @Override
    public List<String> getServerList() throws NacosException {
        List<String> serverList = null;
        try {
            HttpRestResult<String> result = nacosRestTemplate.get(addressServerUrl, header, query, String.class);
            if (result.ok()) {
                serverList = IoUtils.readLines(new StringReader(result.getData())).stream()
                        .filter(StringUtils::isNotBlank).collect(Collectors.toList());
            } else {
                LOGGER.error("read remote server list error, url: {}, code: {}", addressServerUrl, result.getCode());
            }
        } catch (Exception e) {
            LOGGER.error("read remote server list exception, url: {}", addressServerUrl, e);
        }
        return serverList;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public int getOrder() {
        return 0;
    }
    
    @Override
    public String getAddressServerUrl() {
        return addressServerUrl;
    }
    
    @Override
    public boolean supportRefresh() {
        return true;
    }
    
    private void initName() {
        String endpointName = String.join("_", endpoint, String.valueOf(endpointPort), endpointContextPath,
                endpointClusterName);
        String suffix = StringUtils.isNotBlank(namespace) ? ("_" + StringUtils.trim(namespace)) : "";
        this.name = NAME_PREFIX + "-" + endpointName.replaceAll("[/\\\\:]", "_") + suffix;
    }
    
    private void initEndpoint(NacosClientProperties properties) {
        String endpoint = properties.getProperty(PropertyKeyConst.ENDPOINT);
        String isUseEndpointRuleParsing = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE),
                () -> properties.getProperty(PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        this.endpoint =
                Boolean.parseBoolean(isUseEndpointRuleParsing) ? ParamUtil.parsingEndpointRule(endpoint) : endpoint;
    }
    
    private void initEndpointPort(NacosClientProperties properties) {
        String endpointPort = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_PORT));
        this.endpointPort =
                StringUtils.isNotBlank(endpointPort) ? Integer.parseInt(endpointPort) : DEFAULT_ENDPOINT_PORT;
    }
    
    private void initEndpointRetryTime(NacosClientProperties properties) {
        this.endpointRetryTime = NumberUtils.toInt(properties.getProperty("endpointRetryTime"),
                DEFAULT_ENDPOINT_RETRY_TIME);
    }
    
    private void initEndpointContextPath(NacosClientProperties properties) {
        String endpointContextPath = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH));
        this.endpointContextPath =
                StringUtils.isNotBlank(endpointContextPath) ? endpointContextPath : ParamUtil.getDefaultContextPath();
    }
    
    private void initEndpointClusterName(NacosClientProperties properties) {
        String endpointClusterName = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME),
                () -> properties.getProperty(PropertyKeyConst.CLUSTER_NAME));
        this.endpointClusterName =
                StringUtils.isNotBlank(endpointClusterName) ? endpointClusterName : ParamUtil.getDefaultNodesPath();
    }
    
    private void initAddressServerUrl(NacosClientProperties properties, ModuleType moduleType) {
        String endpoint = this.endpoint;
        if (StringUtils.isNotBlank(endpoint)) {
            if (!endpoint.startsWith(RequestUrlConstants.HTTP_PREFIX) && !endpoint.startsWith(
                    RequestUrlConstants.HTTPS_PREFIX)) {
                endpoint = RequestUrlConstants.HTTP_PREFIX + endpoint;
            }
            String contextPath = ContextPathUtil.normalizeContextPath(endpointContextPath);
            String url = String.format("%s:%s%s/%s", endpoint, endpointPort, contextPath, endpointClusterName);
            String params = null;
            if (moduleType == ModuleType.CONFIG) {
                StringJoiner paramJoiner = new StringJoiner("&");
                if (StringUtils.isNotBlank(namespace)) {
                    paramJoiner.add(String.format("namespace=%s", namespace));
                }
                if (properties != null) {
                    String queryParams = properties.getProperty(PropertyKeyConst.ENDPOINT_QUERY_PARAMS);
                    if (StringUtils.isNotBlank(queryParams)) {
                        paramJoiner.add(queryParams);
                    }
                }
                params = paramJoiner.toString();
            }
            this.addressServerUrl = StringUtils.isNotBlank(params) ? url + "?" + params : url;
        }
    }
}
