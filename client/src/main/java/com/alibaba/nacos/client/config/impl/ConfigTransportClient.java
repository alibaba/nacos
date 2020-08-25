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
import com.alibaba.nacos.client.config.http.ServerHttpAgent;
import com.alibaba.nacos.client.identify.StsConfig;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.LogUtils;
import com.alibaba.nacos.client.utils.ParamUtil;
import com.alibaba.nacos.common.constant.HttpHeaderConsts;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.alibaba.nacos.common.utils.ConvertUtils;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.common.utils.UuidUtils;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;

import java.util.Date;
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
public abstract class ConfigTransportClient {
    
    private static final Logger LOGGER = LogUtils.logger(ConfigTransportClient.class);
    
    String encode;
    
    String tenant;
    
    ScheduledExecutorService executor;
    
    final ServerListManager serverListManager;
    
    private int maxRetry = 3;
    
    private final long securityInfoRefreshIntervalMills = TimeUnit.SECONDS.toMillis(5);
    
    protected SecurityProxy securityProxy;
    
    private String accessKey;
    
    private String secretKey;
    
    private volatile StsCredential stsCredential;
    
    public ConfigTransportClient(Properties properties, ServerListManager serverListManager) {
        
        String encodeTmp = properties.getProperty(PropertyKeyConst.ENCODE);
        if (StringUtils.isBlank(encodeTmp)) {
            this.encode = Constants.ENCODE;
        } else {
            this.encode = encodeTmp.trim();
        }
        this.tenant = tenant;
        this.serverListManager = serverListManager;
        this.securityProxy = new SecurityProxy(properties,
                ConfigHttpClientManager.getInstance().getNacosRestTemplate());
        
    }
    
    protected Map<String, String> getSpasHeaders() throws Exception {
        Map<String, String> headers = new HashMap<String, String>();
        
        // STS 临时凭证鉴权的优先级高于 AK/SK 鉴权
        if (StsConfig.getInstance().isStsOn()) {
            StsCredential stsCredential = getStsCredential();
            accessKey = stsCredential.accessKeyId;
            secretKey = stsCredential.accessKeySecret;
            headers.put("Spas-SecurityToken", stsCredential.securityToken);
        }
        
        if (StringUtils.isNotEmpty(accessKey) && StringUtils.isNotEmpty(secretKey)) {
            headers.put("Spas-AccessKey", accessKey);
            //TODO           Map<String, String> signHeaders = SpasAdapter.getSignHeaders(paramValues, secretKey);
            //TODO            if (signHeaders != null) {
            //TODO                headers.putAll(signHeaders);
            //TODO            }
        }
        String ts = String.valueOf(System.currentTimeMillis());
        String token = MD5Utils.md5Hex(ts + ParamUtil.getAppKey(), Constants.ENCODE);
        
        headers.put(Constants.CLIENT_APPNAME_HEADER, ParamUtil.getAppName());
        headers.put(Constants.CLIENT_REQUEST_TS_HEADER, ts);
        headers.put(Constants.CLIENT_REQUEST_TOKEN_HEADER, token);
        headers.put(HttpHeaderConsts.CLIENT_VERSION_HEADER, VersionUtils.version);
        headers.put("exConfigInfo", "true");
        headers.put(HttpHeaderConsts.REQUEST_ID, UuidUtils.generateUuid());
        headers.put(HttpHeaderConsts.ACCEPT_CHARSET, encode);
        return headers;
    }
    
    public String getAcessToken() {
        return securityProxy.getAccessToken();
    }
    
    private StsCredential getStsCredential() throws Exception {
        boolean cacheSecurityCredentials = StsConfig.getInstance().isCacheSecurityCredentials();
        if (cacheSecurityCredentials && stsCredential != null) {
            long currentTime = System.currentTimeMillis();
            long expirationTime = stsCredential.expiration.getTime();
            int timeToRefreshInMillisecond = StsConfig.getInstance().getTimeToRefreshInMillisecond();
            if (expirationTime - currentTime > timeToRefreshInMillisecond) {
                return stsCredential;
            }
        }
        String stsResponse = getStsResponse();
        StsCredential stsCredentialTmp = JacksonUtils.toObj(stsResponse, new TypeReference<StsCredential>() {
        });
        stsCredential = stsCredentialTmp;
        LOGGER.info("[getSTSCredential] code:{}, accessKeyId:{}, lastUpdated:{}, expiration:{}",
                stsCredential.getCode(), stsCredential.getAccessKeyId(), stsCredential.getLastUpdated(),
                stsCredential.getExpiration());
        return stsCredential;
    }
    
    private static String getStsResponse() throws Exception {
        String securityCredentials = StsConfig.getInstance().getSecurityCredentials();
        if (securityCredentials != null) {
            return securityCredentials;
        }
        String securityCredentialsUrl = StsConfig.getInstance().getSecurityCredentialsUrl();
        try {
            HttpRestResult<String> result = ConfigHttpClientManager.getInstance().getNacosRestTemplate()
                    .get(securityCredentialsUrl, Header.EMPTY, Query.EMPTY, String.class);
            
            if (!result.ok()) {
                LOGGER.error(
                        "can not get security credentials, securityCredentialsUrl: {}, responseCode: {}, response: {}",
                        securityCredentialsUrl, result.getCode(), result.getMessage());
                throw new NacosException(NacosException.SERVER_ERROR,
                        "can not get security credentials, responseCode: " + result.getCode() + ", response: " + result
                                .getMessage());
            }
            return result.getData();
        } catch (Exception e) {
            LOGGER.error("can not get security credentials", e);
            throw e;
        }
    }
    
    private void initAkSk(Properties properties) {
        String ramRoleName = properties.getProperty(PropertyKeyConst.RAM_ROLE_NAME);
        if (!StringUtils.isBlank(ramRoleName)) {
            StsConfig.getInstance().setRamRoleName(ramRoleName);
        }
        
        String ak = properties.getProperty(PropertyKeyConst.ACCESS_KEY);
        if (StringUtils.isBlank(ak)) {
            accessKey = SpasAdapter.getAk();
        } else {
            accessKey = ak;
        }
        
        String sk = properties.getProperty(PropertyKeyConst.SECRET_KEY);
        if (StringUtils.isBlank(sk)) {
            secretKey = SpasAdapter.getSk();
        } else {
            secretKey = sk;
        }
    }
    
    private void initMaxRetry(Properties properties) {
        maxRetry = ConvertUtils.toInt(String.valueOf(properties.get(PropertyKeyConst.MAX_RETRY)), Constants.MAX_RETRY);
    }
    
    public void setExecutor(ScheduledExecutorService executor) {
        this.executor = executor;
    }
    
    /**
     * start client.
     */
    public void start2() {
        
        securityProxy.login(serverListManager.getServerUrls());
        
        this.executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                securityProxy.login(serverListManager.getServerUrls());
            }
        }, 0, this.securityInfoRefreshIntervalMills, TimeUnit.MILLISECONDS);
        
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
    
    private static class StsCredential {
        
        @JsonProperty(value = "AccessKeyId")
        private String accessKeyId;
        
        @JsonProperty(value = "AccessKeySecret")
        private String accessKeySecret;
        
        @JsonProperty(value = "Expiration")
        private Date expiration;
        
        @JsonProperty(value = "SecurityToken")
        private String securityToken;
        
        @JsonProperty(value = "LastUpdated")
        private Date lastUpdated;
        
        @JsonProperty(value = "Code")
        private String code;
        
        public String getAccessKeyId() {
            return accessKeyId;
        }
        
        public Date getExpiration() {
            return expiration;
        }
        
        public Date getLastUpdated() {
            return lastUpdated;
        }
        
        public String getCode() {
            return code;
        }
        
        @Override
        public String toString() {
            return "STSCredential{" + "accessKeyId='" + accessKeyId + '\'' + ", accessKeySecret='" + accessKeySecret
                    + '\'' + ", expiration=" + expiration + ", securityToken='" + securityToken + '\''
                    + ", lastUpdated=" + lastUpdated + ", code='" + code + '\'' + '}';
        }
    }
    
}
