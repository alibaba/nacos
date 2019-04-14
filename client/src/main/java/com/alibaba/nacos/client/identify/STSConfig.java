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
package com.alibaba.nacos.client.identify;

import com.alibaba.nacos.client.utils.StringUtils;

/**
 * Sts config
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class STSConfig {
    private static final String RAM_SECURITY_CREDENTIALS_URL
        = "http://100.100.100.200/latest/meta-data/ram/security-credentials/";
    private String ramRoleName;
    /**
     * STS 临时凭证有效期剩余多少时开始刷新（允许本地时间比 STS 服务时间最多慢多久）
     */
    private int timeToRefreshInMillisecond = 3 * 60 * 1000;
    /**
     * 获取 STS 临时凭证的元数据接口（包含角色名称）
     */
    private String securityCredentialsUrl;
    /**
     * 设定 STS 临时凭证，不再通过元数据接口获取
     */
    private String securityCredentials;
    /**
     * 是否缓存
     */
    private boolean cacheSecurityCredentials = true;

    private static class Singleton {
        private static final STSConfig INSTANCE = new STSConfig();
    }

    private STSConfig() {
        String ramRoleName = System.getProperty("ram.role.name");
        if (!StringUtils.isBlank(ramRoleName)) {
            setRamRoleName(ramRoleName);
        }

        String timeToRefreshInMillisecond = System.getProperty("time.to.refresh.in.millisecond");
        if (!StringUtils.isBlank(timeToRefreshInMillisecond)) {
            setTimeToRefreshInMillisecond(Integer.parseInt(timeToRefreshInMillisecond));
        }

        String securityCredentials = System.getProperty("security.credentials");
        if (!StringUtils.isBlank(securityCredentials)) {
            setSecurityCredentials(securityCredentials);
        }

        String securityCredentialsUrl = System.getProperty("security.credentials.url");
        if (!StringUtils.isBlank(securityCredentialsUrl)) {
            setSecurityCredentialsUrl(securityCredentialsUrl);
        }

        String cacheSecurityCredentials = System.getProperty("cache.security.credentials");
        if (!StringUtils.isBlank(cacheSecurityCredentials)) {
            setCacheSecurityCredentials(Boolean.valueOf(cacheSecurityCredentials));
        }
    }

    public static STSConfig getInstance() {
        return Singleton.INSTANCE;
    }

    public String getRamRoleName() {
        return ramRoleName;
    }

    public void setRamRoleName(String ramRoleName) {
        this.ramRoleName = ramRoleName;
    }

    public int getTimeToRefreshInMillisecond() {
        return timeToRefreshInMillisecond;
    }

    public void setTimeToRefreshInMillisecond(int timeToRefreshInMillisecond) {
        this.timeToRefreshInMillisecond = timeToRefreshInMillisecond;
    }

    public String getSecurityCredentialsUrl() {
        if (securityCredentialsUrl == null && ramRoleName != null) {
            return RAM_SECURITY_CREDENTIALS_URL + ramRoleName;
        }
        return securityCredentialsUrl;
    }

    public void setSecurityCredentialsUrl(String securityCredentialsUrl) {
        this.securityCredentialsUrl = securityCredentialsUrl;
    }

    public String getSecurityCredentials() {
        return securityCredentials;
    }

    public void setSecurityCredentials(String securityCredentials) {
        this.securityCredentials = securityCredentials;
    }

    public boolean isSTSOn() {
        return StringUtils.isNotEmpty(getSecurityCredentials()) || StringUtils.isNotEmpty(getSecurityCredentialsUrl());
    }

    public boolean isCacheSecurityCredentials() {
        return cacheSecurityCredentials;
    }

    public void setCacheSecurityCredentials(boolean cacheSecurityCredentials) {
        this.cacheSecurityCredentials = cacheSecurityCredentials;
    }
}
