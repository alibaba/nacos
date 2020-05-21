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
package com.alibaba.nacos.client.config;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.config.filter.impl.ConfigFilterChainManager;
import com.alibaba.nacos.client.config.filter.impl.ConfigRequest;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.config.http.HttpAgent;
import com.alibaba.nacos.client.config.http.MetricsHttpAgent;
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.config.impl.ClientWorker;
import com.alibaba.nacos.client.config.impl.HttpSimpleClient.HttpResult;
import com.alibaba.nacos.client.config.impl.LocalConfigInfoProcessor;
import com.alibaba.nacos.client.config.utils.ContentUtils;
import com.alibaba.nacos.client.config.utils.ParamUtils;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.client.utils.ValidatorUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Config Impl
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ServiceOrDaoClassShouldEndWithImplRule")
public class NacosConfigService implements ConfigService {

    private static final Logger LOGGER = LogUtils.logger(NacosConfigService.class);

    private static final long POST_TIMEOUT = 3000L;

    /**
     * http agent
     */
    private HttpAgent agent;
    /**
     * longpolling
     */
    private ClientWorker worker;
    private String namespace;
    private String encode;
    private ConfigFilterChainManager configFilterChainManager = new ConfigFilterChainManager();

    public NacosConfigService(Properties properties) throws NacosException {
        ValidatorUtils.checkInitParam(properties);
        String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
        if (StringUtils.isBlank(encodeTmp)) {
            encode = Constants.ENCODE;
        } else {
            encode = encodeTmp.trim();
        }
        initNamespace(properties);
        agent = new MetricsHttpAgent(new ServerHttpAgent(properties));
        agent.start();
        worker = new ClientWorker(agent, configFilterChainManager, properties);
    }

    private void initNamespace(Properties properties) {
        namespace = ParamUtil.parseNamespace(properties);
        properties.put(PropertyKeyConst.NAMESPACE, namespace);
    }

    @Override
    public String getConfig(String dataId, String group, long timeoutMs) throws NacosException {
        return getConfigInner(namespace, dataId, group, timeoutMs);
    }

    @Override
    public String getConfigAndSignListener(String dataId, String group, long timeoutMs, Listener listener) throws NacosException {
        String content = getConfig(dataId, group, timeoutMs);
        worker.addTenantListenersWithContent(dataId, group, content, Arrays.asList(listener));
        return content;
    }

    @Override
    public void addListener(String dataId, String group, Listener listener) throws NacosException {
        worker.addTenantListeners(dataId, group, Arrays.asList(listener));
    }

    @Override
    public boolean publishConfig(String dataId, String group, String content) throws NacosException {
        return publishConfigInner(namespace, dataId, group, null, null, null, content);
    }

    @Override
    public boolean removeConfig(String dataId, String group) throws NacosException {
        return removeConfigInner(namespace, dataId, group, null);
    }

    @Override
    public void removeListener(String dataId, String group, Listener listener) {
        worker.removeTenantListener(dataId, group, listener);
    }

    private String getConfigInner(String tenant, String dataId, String group, long timeoutMs) throws NacosException {
        group = null2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        ConfigResponse cr = new ConfigResponse();

        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);

        // 优先使用本地配置
        String content = LocalConfigInfoProcessor.getFailover(agent.getName(), dataId, group, tenant);
        if (content != null) {
            LOGGER.warn("[{}] [get-config] get failover ok, dataId={}, group={}, tenant={}, config={}", agent.getName(),
                dataId, group, tenant, ContentUtils.truncateContent(content));
            cr.setContent(content);
            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();
            return content;
        }

        try {
            String[] ct = worker.getServerConfig(dataId, group, tenant, timeoutMs);
            cr.setContent(ct[0]);

            configFilterChainManager.doFilter(null, cr);
            content = cr.getContent();

            return content;
        } catch (NacosException ioe) {
            if (NacosException.NO_RIGHT == ioe.getErrCode()) {
                throw ioe;
            }
            LOGGER.warn("[{}] [get-config] get from server error, dataId={}, group={}, tenant={}, msg={}",
                agent.getName(), dataId, group, tenant, ioe.toString());
        }

        LOGGER.warn("[{}] [get-config] get snapshot ok, dataId={}, group={}, tenant={}, config={}", agent.getName(),
            dataId, group, tenant, ContentUtils.truncateContent(content));
        content = LocalConfigInfoProcessor.getSnapshot(agent.getName(), dataId, group, tenant);
        cr.setContent(content);
        configFilterChainManager.doFilter(null, cr);
        content = cr.getContent();
        return content;
    }

    private String null2defaultGroup(String group) {
        return (null == group) ? Constants.DEFAULT_GROUP : group.trim();
    }

    private boolean removeConfigInner(String tenant, String dataId, String group, String tag) throws NacosException {
        group = null2defaultGroup(group);
        ParamUtils.checkKeyParam(dataId, group);
        String url = Constants.CONFIG_CONTROLLER_PATH;
        List<String> params = new ArrayList<String>();
        params.add("dataId");
        params.add(dataId);
        params.add("group");
        params.add(group);
        if (StringUtils.isNotEmpty(tenant)) {
            params.add("tenant");
            params.add(tenant);
        }
        if (StringUtils.isNotEmpty(tag)) {
            params.add("tag");
            params.add(tag);
        }
        HttpResult result = null;
        try {
            result = agent.httpDelete(url, null, params, encode, POST_TIMEOUT);
        } catch (IOException ioe) {
            LOGGER.warn("[remove] error, " + dataId + ", " + group + ", " + tenant + ", msg: " + ioe.toString());
            return false;
        }

        if (HttpURLConnection.HTTP_OK == result.code) {
            LOGGER.info("[{}] [remove] ok, dataId={}, group={}, tenant={}", agent.getName(), dataId, group, tenant);
            return true;
        } else if (HttpURLConnection.HTTP_FORBIDDEN == result.code) {
            LOGGER.warn("[{}] [remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(), dataId,
                group, tenant, result.code, result.content);
            throw new NacosException(result.code, result.content);
        } else {
            LOGGER.warn("[{}] [remove] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(), dataId,
                group, tenant, result.code, result.content);
            return false;
        }
    }

    private boolean publishConfigInner(String tenant, String dataId, String group, String tag, String appName,
                                       String betaIps, String content) throws NacosException {
        group = null2defaultGroup(group);
        ParamUtils.checkParam(dataId, group, content);

        ConfigRequest cr = new ConfigRequest();
        cr.setDataId(dataId);
        cr.setTenant(tenant);
        cr.setGroup(group);
        cr.setContent(content);
        configFilterChainManager.doFilter(cr, null);
        content = cr.getContent();

        String url = Constants.CONFIG_CONTROLLER_PATH;
        List<String> params = new ArrayList<String>();
        params.add("dataId");
        params.add(dataId);
        params.add("group");
        params.add(group);
        params.add("content");
        params.add(content);
        if (StringUtils.isNotEmpty(tenant)) {
            params.add("tenant");
            params.add(tenant);
        }
        if (StringUtils.isNotEmpty(appName)) {
            params.add("appName");
            params.add(appName);
        }
        if (StringUtils.isNotEmpty(tag)) {
            params.add("tag");
            params.add(tag);
        }

        List<String> headers = new ArrayList<String>();
        if (StringUtils.isNotEmpty(betaIps)) {
            headers.add("betaIps");
            headers.add(betaIps);
        }

        HttpResult result = null;
        try {
            result = agent.httpPost(url, headers, params, encode, POST_TIMEOUT);
        } catch (IOException ioe) {
            LOGGER.warn("[{}] [publish-single] exception, dataId={}, group={}, msg={}", agent.getName(), dataId,
                group, ioe.toString());
            return false;
        }

        if (HttpURLConnection.HTTP_OK == result.code) {
            LOGGER.info("[{}] [publish-single] ok, dataId={}, group={}, tenant={}, config={}", agent.getName(), dataId,
                group, tenant, ContentUtils.truncateContent(content));
            return true;
        } else if (HttpURLConnection.HTTP_FORBIDDEN == result.code) {
            LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(),
                dataId, group, tenant, result.code, result.content);
            throw new NacosException(result.code, result.content);
        } else {
            LOGGER.warn("[{}] [publish-single] error, dataId={}, group={}, tenant={}, code={}, msg={}", agent.getName(),
                dataId, group, tenant, result.code, result.content);
            return false;
        }

    }

    @Override
    public String getServerStatus() {
        if (worker.isHealthServer()) {
            return "UP";
        } else {
            return "DOWN";
        }
    }

}
