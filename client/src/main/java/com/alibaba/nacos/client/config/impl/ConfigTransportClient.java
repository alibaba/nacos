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
import com.alibaba.nacos.common.utils.StringUtils;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;

/**
 * config transport client,include basic operations of config module.
 *
 * @author liuzunfei
 * @version $Id: ConfigTransportClient.java, v 0.1 2020年08月24日 2:01 PM liuzunfei Exp $
 */
public abstract class ConfigTransportClient {
    
    String encode;
    
    String tenant;
    
    ScheduledExecutorService executor;
    
    final ServerListManager serverListManager;
    
    private String accessKey;
    
    private String secretKey;
    
    private int maxRetry = 3;
    
    public ConfigTransportClient(Properties properties, ServerListManager serverListManager) {
        
        String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
        if (StringUtils.isBlank(encodeTmp)) {
            this.encode = Constants.ENCODE;
        } else {
            this.encode = encodeTmp.trim();
        }
        this.tenant = tenant;
        this.serverListManager = serverListManager;
    }
    
    public void setExecutor(ScheduledExecutorService executor) {
        this.executor = executor;
    }
    
    public abstract void start() throws NacosException;
    
    /**
     * get client name.
     *
     * @return
     */
    public abstract String getName();
    
    /**
     * get encode.
     *
     * @return
     */
    public String getEncode() {
        return this.encode;
    }
    
    /**
     * get tenant.
     *
     * @return
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
     *
     * @return
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
     * @param dataId dataId
     * @param group  group
     * @param tenat  tenat
     * @return ConfigQueryResponse.
     * @throws NacosException throw where query fail .
     */
    public abstract String[] queryConfig(String dataId, String group, String tenat, long readTimeous)
            throws NacosException;
    
    /**
     * publish config.
     *
     * @param dataId dataId
     * @param group  group
     * @param tenant tenant
     * @return push  result.
     * @throws NacosException throw where publish fail.
     */
    public abstract boolean publishConfig(String dataId, String group, String tenant, String appName, String tag,
            String betaIps, String content) throws NacosException;
    
    /**
     * remove config.
     *
     * @param dataid dataid
     * @param group  group
     * @param tenat  tenat
     * @return response.
     * @throws NacosException throw where publish fail.
     */
    public abstract boolean removeConfig(String dataid, String group, String tenat, String tag) throws NacosException;
}
