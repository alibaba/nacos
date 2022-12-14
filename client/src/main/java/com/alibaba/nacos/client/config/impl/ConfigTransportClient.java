/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.config.impl;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.plugin.auth.api.RequestResource;
import com.alibaba.nacos.client.config.filter.impl.ConfigResponse;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * config transport client,include basic operations of config module.
 *
 * @author liuzunfei
 * @version $Id: ConfigTransportClient.java, v 0.1 2020年08月24日 2:01 PM liuzunfei Exp $
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class ConfigTransportClient {
    
    private static final String CONFIG_INFO_HEADER = "exConfigInfo";
    
    private static final String DEFAULT_CONFIG_INFO = "true";
    
    String encode;
    
    String tenant;
    
    ScheduledExecutorService executor;
    
    final ServerListManager serverListManager;
    
    final Properties properties;
    
    private int maxRetry = 3;
    
    private final long securityInfoRefreshIntervalMills = TimeUnit.SECONDS.toMillis(5);
    
    protected SecurityProxy securityProxy;
    
    public void shutdown() throws NacosException {
        securityProxy.shutdown();
    }
    
    public ConfigTransportClient(NacosClientProperties properties, ServerListManager serverListManager) {
        
        String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
        if (StringUtils.isBlank(encodeTmp)) {
            this.encode = Constants.ENCODE;
        } else {
            this.encode = encodeTmp.trim();
        }
        
        this.tenant = properties.getProperty(PropertyKeyConst.NAMESPACE);
        this.serverListManager = serverListManager;
        this.properties = properties.asProperties();
        this.securityProxy = new SecurityProxy(serverListManager.getServerUrls(),
                ConfigHttpClientManager.getInstance().getNacosRestTemplate());
    }
    
    /**
     * Build the resource for current request.
     *
     * @param tenant tenant of config
     * @param group  group of config
     * @param dataId dataId of config
     * @return resource
     */
    protected RequestResource buildResource(String tenant, String group, String dataId) {
        return RequestResource.configBuilder().setNamespace(tenant).setGroup(group).setResource(dataId).build();
    }
    
    protected Map<String, String> getSecurityHeaders(RequestResource resource) throws Exception {
        return securityProxy.getIdentityContext(resource);
    }
    
    /**
     * get common header.
     *
     * @return headers.
     */
    protected Map<String, String> getCommonHeader() {
        Map<String, String> headers = new HashMap<>(16);
        
        String ts = String.valueOf(System.currentTimeMillis());
        String token = MD5Utils.md5Hex(ts + ParamUtil.getAppKey(), Constants.ENCODE);
        
        headers.put(Constants.CLIENT_APPNAME_HEADER, ParamUtil.getAppName());
        headers.put(Constants.CLIENT_REQUEST_TS_HEADER, ts);
        headers.put(Constants.CLIENT_REQUEST_TOKEN_HEADER, token);
        headers.put(CONFIG_INFO_HEADER, DEFAULT_CONFIG_INFO);
        headers.put(Constants.CHARSET_KEY, encode);
        return headers;
    }
    
    private void initMaxRetry(Properties properties) {
        maxRetry = ConvertUtils.toInt(String.valueOf(properties.get(PropertyKeyConst.MAX_RETRY)), Constants.MAX_RETRY);
    }
    
    public void setExecutor(ScheduledExecutorService executor) {
        this.executor = executor;
    }
    
    /**
     * base start client.
     */
    public void start() throws NacosException {
        securityProxy.login(this.properties);
        this.executor.scheduleWithFixedDelay(() -> securityProxy.login(properties), 0,
                this.securityInfoRefreshIntervalMills, TimeUnit.MILLISECONDS);
        startInternal();
    }
    
    /**
     * start client inner.
     *
     * @throws NacosException exception may throw.
     */
    public abstract void startInternal() throws NacosException;
    
    /**
     * get client name.
     *
     * @return name.
     */
    public abstract String getName();
    
    /**
     * get encode.
     *
     * @return encode.
     */
    public String getEncode() {
        return this.encode;
    }
    
    /**
     * get tenant.
     *
     * @return tenant.
     */
    public String getTenant() {
        return this.tenant;
    }
    
    /**
     * notify listen config.
     **/
    public abstract void notifyListenConfig();
    
    /**
     * listen change .
     */
    public abstract void executeConfigListen();
    
    /**
     * remove cache implements.
     *
     * @param dataId dataId.
     * @param group  group
     */
    public abstract void removeCache(String dataId, String group);
    
    /**
     * query config.
     *
     * @param dataId      dataId.
     * @param group       group.
     * @param tenat       tenat.
     * @param readTimeous readTimeous.
     * @param notify      query for notify sync.
     * @return content.
     * @throws NacosException throw where query fail .
     */
    public abstract ConfigResponse queryConfig(String dataId, String group, String tenat, long readTimeous,
            boolean notify) throws NacosException;
    
    /**
     * publish config.
     *
     * @param dataId           dataId.
     * @param group            group.
     * @param tenant           tenant.
     * @param appName          appName.
     * @param tag              tag.
     * @param betaIps          betaIps.
     * @param content          content.
     * @param encryptedDataKey encryptedDataKey
     * @param casMd5           casMd5.
     * @param type             type.
     * @return success or not.
     * @throws NacosException throw where publish fail.
     */
    public abstract boolean publishConfig(String dataId, String group, String tenant, String appName, String tag,
            String betaIps, String content, String encryptedDataKey, String casMd5, String type) throws NacosException;
    
    /**
     * remove config.
     *
     * @param dataid dataid.
     * @param group  group.
     * @param tenat  tenat.
     * @param tag    tag.
     * @return success or not.
     * @throws NacosException throw where publish fail.
     */
    public abstract boolean removeConfig(String dataid, String group, String tenat, String tag) throws NacosException;
    
}
