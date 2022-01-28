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

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Sts config.
 *
 * @author Nacos
 */
public class StsConfig {
    
    private static final String RAM_SECURITY_CREDENTIALS_URL = "http://100.100.100.200/latest/meta-data/ram/security-credentials/";
    
    private String ramRoleName;
    
    /**
     * The STS temporary certificate will be refreshed when the validity period of
     * the temporary certificate is left (allow the local time to be at most slower than the STS service time).
     */
    private int timeToRefreshInMillisecond = 3 * 60 * 1000;
    
    /**
     * Metadata interface for obtaining STS temporary credentials (including role name).
     */
    private String securityCredentialsUrl;
    
    /**
     * Set the STS temporary certificate and no longer obtain it through the metadata interface.
     */
    private String securityCredentials;
    
    /**
     * Whether to cache.
     */
    private boolean cacheSecurityCredentials = true;
    
    private static class Singleton {
        
        private static final StsConfig INSTANCE = new StsConfig();
    }
    
    private StsConfig() {
        String ramRoleName = System.getProperty(IdentifyConstants.RAM_ROLE_NAME_PROPERTY);
        if (!StringUtils.isBlank(ramRoleName)) {
            setRamRoleName(ramRoleName);
        }
        
        String timeToRefreshInMillisecond = System.getProperty(IdentifyConstants.REFRESH_TIME_PROPERTY);
        if (!StringUtils.isBlank(timeToRefreshInMillisecond)) {
            setTimeToRefreshInMillisecond(Integer.parseInt(timeToRefreshInMillisecond));
        }
        
        String securityCredentials = System.getProperty(IdentifyConstants.SECURITY_PROPERTY);
        if (!StringUtils.isBlank(securityCredentials)) {
            setSecurityCredentials(securityCredentials);
        }
        
        String securityCredentialsUrl = System.getProperty(IdentifyConstants.SECURITY_URL_PROPERTY);
        if (!StringUtils.isBlank(securityCredentialsUrl)) {
            setSecurityCredentialsUrl(securityCredentialsUrl);
        }
        
        String cacheSecurityCredentials = System.getProperty(IdentifyConstants.SECURITY_CACHE_PROPERTY);
        if (!StringUtils.isBlank(cacheSecurityCredentials)) {
            setCacheSecurityCredentials(Boolean.parseBoolean(cacheSecurityCredentials));
        }
    }
    
    public static StsConfig getInstance() {
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
    
    public boolean isStsOn() {
        return StringUtils.isNotEmpty(getSecurityCredentials()) || StringUtils.isNotEmpty(getSecurityCredentialsUrl());
    }
    
    public boolean isCacheSecurityCredentials() {
        return cacheSecurityCredentials;
    }
    
    public void setCacheSecurityCredentials(boolean cacheSecurityCredentials) {
        this.cacheSecurityCredentials = cacheSecurityCredentials;
    }
}
