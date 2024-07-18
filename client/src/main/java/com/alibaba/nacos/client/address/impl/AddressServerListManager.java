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
import com.alibaba.nacos.api.SystemPropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.address.base.AbstractServerListManager;
import com.alibaba.nacos.client.address.base.Order;
import com.alibaba.nacos.client.config.impl.ConfigHttpClientManager;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.utils.ContextPathUtil;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.TemplateUtils;
import com.alibaba.nacos.common.constant.RequestUrlConstants;
import com.alibaba.nacos.common.executor.NameThreadFactory;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.NumberUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringReader;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Address Server List Manager.
 *
 * @author misakacoder
 */
@Order(0)
public class AddressServerListManager extends AbstractServerListManager {

    private static final String NAME_PREFIX = "address-server";

    private static final int DEFAULT_ENDPOINT_PORT = 8080;

    private static final int DEFAULT_INIT_SERVER_LIST_RETRY_TIME = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(AddressServerListManager.class);

    private String endpoint;

    private int endpointPort;

    private String endpointContextPath;

    private String endpointClusterName;

    private final String addressServerUrl;

    private final int initServerListRetryTime;

    private final NacosRestTemplate nacosRestTemplate;

    private final ScheduledExecutorService scheduledExecutorService;

    public AddressServerListManager(NacosClientProperties properties) throws NacosException {
        super(properties);
        this.addressServerUrl = initAddressServerUrl(properties);
        this.initServerListRetryTime = initServerListRetryTime(properties);
        this.nacosRestTemplate = ConfigHttpClientManager.getInstance().getNacosRestTemplate();
        this.scheduledExecutorService = new ScheduledThreadPoolExecutor(1, new NameThreadFactory(this.getClass().getName()));
        initServerList();
    }

    @Override
    protected String initServerName(NacosClientProperties properties) {
        endpoint = initEndpoint(properties);
        endpointPort = initEndpointPort(properties);
        endpointContextPath = initEndpointContextPath(properties);
        endpointClusterName = initEndpointClusterName(properties);
        return NAME_PREFIX + "-"
                + String.join("_", endpoint.replaceAll("http(s)?://", ""), String.valueOf(endpointPort), endpointContextPath, endpointClusterName);
    }

    @Override
    public void shutdown() throws NacosException {
        ThreadUtils.shutdownThreadPool(scheduledExecutorService, LOGGER);
    }

    private String initEndpoint(NacosClientProperties properties) {
        String endpoint = properties.getProperty(PropertyKeyConst.ENDPOINT);
        String isUseEndpointRuleParsing = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(SystemPropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE),
                () -> properties.getProperty(
                        PropertyKeyConst.IS_USE_ENDPOINT_PARSING_RULE,
                        String.valueOf(ParamUtil.USE_ENDPOINT_PARSING_RULE_DEFAULT_VALUE)));
        return Boolean.parseBoolean(isUseEndpointRuleParsing) ? ParamUtil.parsingEndpointRule(endpoint) : endpoint;
    }

    private int initEndpointPort(NacosClientProperties properties) {
        String endpointPort = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_PORT),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_PORT));
        return StringUtils.isNotBlank(endpointPort) ? Integer.parseInt(endpointPort) : DEFAULT_ENDPOINT_PORT;
    }

    private String initEndpointContextPath(NacosClientProperties properties) {
        String endpointContextPath = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.SystemEnv.ALIBABA_ALIWARE_ENDPOINT_CONTEXT_PATH),
                () -> properties.getProperty(PropertyKeyConst.ENDPOINT_CONTEXT_PATH));
        return StringUtils.isNotBlank(endpointContextPath) ? endpointContextPath : ParamUtil.getDefaultContextPath();
    }

    private String initEndpointClusterName(NacosClientProperties properties) {
        String endpointClusterName = TemplateUtils.stringBlankAndThenExecute(
                properties.getProperty(PropertyKeyConst.ENDPOINT_CLUSTER_NAME),
                () -> properties.getProperty(PropertyKeyConst.CLUSTER_NAME));
        return StringUtils.isNotBlank(endpointClusterName) ? endpointClusterName : ParamUtil.getDefaultNodesPath();
    }

    private String initAddressServerUrl(NacosClientProperties properties) {
        String addressServerUrl = "";
        String endpoint = this.endpoint;
        if (StringUtils.isNotBlank(endpoint)) {
            if (!endpoint.startsWith(RequestUrlConstants.HTTP_PREFIX) && !endpoint.startsWith(RequestUrlConstants.HTTPS_PREFIX)) {
                endpoint = RequestUrlConstants.HTTP_PREFIX + endpoint;
            }
            String contextPath = ContextPathUtil.normalizeContextPath(endpointContextPath);
            String url = String.format("%s:%s%s/%s", endpoint, endpointPort, contextPath, endpointClusterName);
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
            String params = paramJoiner.toString();
            addressServerUrl = StringUtils.isNotBlank(params) ? url + "?" + params : url;
        }
        return addressServerUrl;
    }

    private int initServerListRetryTime(NacosClientProperties properties) {
        return NumberUtils.toInt(properties.getProperty("initServerListRetryTime"), DEFAULT_INIT_SERVER_LIST_RETRY_TIME);
    }

    /**
     * Initializes the server list from address server url.
     */
    private void initServerList() {
        if (StringUtils.isNotBlank(addressServerUrl)) {
            LOGGER.info("init server from address url: {}", addressServerUrl);
            Runnable task = createUpdateServerListTask(this::readServerList);
            for (int i = 0; i < initServerListRetryTime; ++i) {
                task.run();
                if (!getServerList().isEmpty()) {
                    break;
                }
                try {
                    TimeUnit.SECONDS.sleep(1L);
                } catch (InterruptedException ignored) {

                }
            }
            if (!getServerList().isEmpty()) {
                scheduledExecutorService.scheduleWithFixedDelay(task, 0L, 30L, TimeUnit.SECONDS);
            }
        }
    }

    private List<String> readServerList() {
        try {
            HttpRestResult<String> result = nacosRestTemplate.get(addressServerUrl, Header.EMPTY, Query.EMPTY, String.class);
            if (result.ok()) {
                return readServerList(new StringReader(result.getData()));
            } else {
                LOGGER.error("read remote server list error, url: {}, code: {}", addressServerUrl, result.getCode());
            }
        } catch (Exception e) {
            LOGGER.error("read remote server list exception, url: {}", addressServerUrl, e);
        }
        return null;
    }
}
