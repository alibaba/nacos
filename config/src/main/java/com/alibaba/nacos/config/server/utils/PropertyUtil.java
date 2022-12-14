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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.PropertiesConstant;
import com.alibaba.nacos.sys.env.EnvUtil;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Properties util.
 *
 * @author Nacos
 */
public class PropertyUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    
    private static final Logger LOGGER = LogUtil.DEFAULT_LOG;
    
    private static int notifyConnectTimeout = 100;
    
    private static int notifySocketTimeout = 200;
    
    private static int maxHealthCheckFailCount = 12;
    
    private static boolean isHealthCheck = true;
    
    private static int maxContent = 10 * 1024 * 1024;
    
    /**
     * Whether to enable capacity management.
     */
    private static boolean isManageCapacity = true;
    
    /**
     * Whether to enable the limit check function of capacity management, including the upper limit of configuration
     * number, configuration content size limit, etc.
     */
    private static boolean isCapacityLimitCheck = false;
    
    /**
     * The default cluster capacity limit.
     */
    private static int defaultClusterQuota = 100000;
    
    /**
     * the default capacity limit per Group.
     */
    private static int defaultGroupQuota = 200;
    
    /**
     * The default capacity limit per Tenant.
     */
    private static int defaultTenantQuota = 200;
    
    /**
     * The maximum size of the content in the configuration of a single, unit for bytes.
     */
    private static int defaultMaxSize = 100 * 1024;
    
    /**
     * The default Maximum number of aggregated data.
     */
    private static int defaultMaxAggrCount = 10000;
    
    /**
     * The maximum size of content in a single subconfiguration of aggregated data.
     */
    private static int defaultMaxAggrSize = 1024;
    
    /**
     * Initialize the expansion percentage of capacity has reached the limit.
     */
    private static int initialExpansionPercent = 100;
    
    /**
     * Fixed capacity information table usage (usage) time interval, the unit is in seconds.
     */
    private static int correctUsageDelay = 10 * 60;
    
    /**
     * Standalone mode uses DB.
     */
    private static boolean useExternalDB = false;
    
    /**
     * Inline storage value = ${nacos.standalone}.
     */
    private static boolean embeddedStorage = EnvUtil.getStandaloneMode();
    
    public static int getNotifyConnectTimeout() {
        return notifyConnectTimeout;
    }
    
    public static void setNotifyConnectTimeout(int notifyConnectTimeout) {
        PropertyUtil.notifyConnectTimeout = notifyConnectTimeout;
    }
    
    public static int getNotifySocketTimeout() {
        return notifySocketTimeout;
    }
    
    public static void setNotifySocketTimeout(int notifySocketTimeout) {
        PropertyUtil.notifySocketTimeout = notifySocketTimeout;
    }
    
    public static int getMaxHealthCheckFailCount() {
        return maxHealthCheckFailCount;
    }
    
    public static void setMaxHealthCheckFailCount(int maxHealthCheckFailCount) {
        PropertyUtil.maxHealthCheckFailCount = maxHealthCheckFailCount;
    }
    
    public static boolean isHealthCheck() {
        return isHealthCheck;
    }
    
    public static void setHealthCheck(boolean isHealthCheck) {
        PropertyUtil.isHealthCheck = isHealthCheck;
    }
    
    public static int getMaxContent() {
        return maxContent;
    }
    
    public static void setMaxContent(int maxContent) {
        PropertyUtil.maxContent = maxContent;
    }
    
    public static boolean isManageCapacity() {
        return isManageCapacity;
    }
    
    public static void setManageCapacity(boolean isManageCapacity) {
        PropertyUtil.isManageCapacity = isManageCapacity;
    }
    
    public static int getDefaultClusterQuota() {
        return defaultClusterQuota;
    }
    
    public static void setDefaultClusterQuota(int defaultClusterQuota) {
        PropertyUtil.defaultClusterQuota = defaultClusterQuota;
    }
    
    public static boolean isCapacityLimitCheck() {
        return isCapacityLimitCheck;
    }
    
    public static void setCapacityLimitCheck(boolean isCapacityLimitCheck) {
        PropertyUtil.isCapacityLimitCheck = isCapacityLimitCheck;
    }
    
    public static int getDefaultGroupQuota() {
        return defaultGroupQuota;
    }
    
    public static void setDefaultGroupQuota(int defaultGroupQuota) {
        PropertyUtil.defaultGroupQuota = defaultGroupQuota;
    }
    
    public static int getDefaultTenantQuota() {
        return defaultTenantQuota;
    }
    
    public static void setDefaultTenantQuota(int defaultTenantQuota) {
        PropertyUtil.defaultTenantQuota = defaultTenantQuota;
    }
    
    public static int getInitialExpansionPercent() {
        return initialExpansionPercent;
    }
    
    public static void setInitialExpansionPercent(int initialExpansionPercent) {
        PropertyUtil.initialExpansionPercent = initialExpansionPercent;
    }
    
    public static int getDefaultMaxSize() {
        return defaultMaxSize;
    }
    
    public static void setDefaultMaxSize(int defaultMaxSize) {
        PropertyUtil.defaultMaxSize = defaultMaxSize;
    }
    
    public static int getDefaultMaxAggrCount() {
        return defaultMaxAggrCount;
    }
    
    public static void setDefaultMaxAggrCount(int defaultMaxAggrCount) {
        PropertyUtil.defaultMaxAggrCount = defaultMaxAggrCount;
    }
    
    public static int getDefaultMaxAggrSize() {
        return defaultMaxAggrSize;
    }
    
    public static void setDefaultMaxAggrSize(int defaultMaxAggrSize) {
        PropertyUtil.defaultMaxAggrSize = defaultMaxAggrSize;
    }
    
    public static int getCorrectUsageDelay() {
        return correctUsageDelay;
    }
    
    public static void setCorrectUsageDelay(int correctUsageDelay) {
        PropertyUtil.correctUsageDelay = correctUsageDelay;
    }
    
    public static boolean isStandaloneMode() {
        return EnvUtil.getStandaloneMode();
    }
    
    public static boolean isUseExternalDB() {
        return useExternalDB;
    }
    
    public static void setUseExternalDB(boolean useExternalDB) {
        PropertyUtil.useExternalDB = useExternalDB;
    }
    
    public static boolean isEmbeddedStorage() {
        return embeddedStorage;
    }
    
    // Determines whether to read the data directly
    // if use mysql, Reduce database read pressure
    // if use raft+derby, Reduce leader read pressure
    
    public static boolean isDirectRead() {
        return EnvUtil.getStandaloneMode() && isEmbeddedStorage();
    }
    
    public static void setEmbeddedStorage(boolean embeddedStorage) {
        PropertyUtil.embeddedStorage = embeddedStorage;
    }
    
    private void loadSetting() {
        try {
            setNotifyConnectTimeout(Integer.parseInt(EnvUtil.getProperty(PropertiesConstant.NOTIFY_CONNECT_TIMEOUT,
                    String.valueOf(notifyConnectTimeout))));
            LOGGER.info("notifyConnectTimeout:{}", notifyConnectTimeout);
            setNotifySocketTimeout(Integer.parseInt(EnvUtil.getProperty(PropertiesConstant.NOTIFY_SOCKET_TIMEOUT,
                    String.valueOf(notifySocketTimeout))));
            LOGGER.info("notifySocketTimeout:{}", notifySocketTimeout);
            setHealthCheck(Boolean.parseBoolean(
                    EnvUtil.getProperty(PropertiesConstant.IS_HEALTH_CHECK, String.valueOf(isHealthCheck))));
            LOGGER.info("isHealthCheck:{}", isHealthCheck);
            setMaxHealthCheckFailCount(Integer.parseInt(
                    EnvUtil.getProperty(PropertiesConstant.MAX_HEALTH_CHECK_FAIL_COUNT,
                            String.valueOf(maxHealthCheckFailCount))));
            LOGGER.info("maxHealthCheckFailCount:{}", maxHealthCheckFailCount);
            setMaxContent(
                    Integer.parseInt(EnvUtil.getProperty(PropertiesConstant.MAX_CONTENT, String.valueOf(maxContent))));
            LOGGER.info("maxContent:{}", maxContent);
            // capacity management
            setManageCapacity(getBoolean(PropertiesConstant.IS_MANAGE_CAPACITY, isManageCapacity));
            setCapacityLimitCheck(getBoolean(PropertiesConstant.IS_CAPACITY_LIMIT_CHECK, isCapacityLimitCheck));
            setDefaultClusterQuota(getInt(PropertiesConstant.DEFAULT_CLUSTER_QUOTA, defaultClusterQuota));
            setDefaultGroupQuota(getInt(PropertiesConstant.DEFAULT_GROUP_QUOTA, defaultGroupQuota));
            setDefaultTenantQuota(getInt(PropertiesConstant.DEFAULT_TENANT_QUOTA, defaultTenantQuota));
            setDefaultMaxSize(getInt(PropertiesConstant.DEFAULT_MAX_SIZE, defaultMaxSize));
            setDefaultMaxAggrCount(getInt(PropertiesConstant.DEFAULT_MAX_AGGR_COUNT, defaultMaxAggrCount));
            setDefaultMaxAggrSize(getInt(PropertiesConstant.DEFAULT_MAX_AGGR_SIZE, defaultMaxAggrSize));
            setCorrectUsageDelay(getInt(PropertiesConstant.CORRECT_USAGE_DELAY, correctUsageDelay));
            setInitialExpansionPercent(getInt(PropertiesConstant.INITIAL_EXPANSION_PERCENT, initialExpansionPercent));
    
            // External data sources are used by default in cluster mode
            String platform = DatasourcePlatformUtil.getDatasourcePlatform("");
            boolean useExternalStorage = !PropertiesConstant.EMPTY_DATASOURCE_PLATFORM.equalsIgnoreCase(platform)
                    && !PropertiesConstant.DERBY.equalsIgnoreCase(platform);
            setUseExternalDB(useExternalStorage);
            
            // must initialize after setUseExternalDB
            // This value is true in stand-alone mode and false in cluster mode
            // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
            // default value is depend on ${nacos.standalone}
            
            if (isUseExternalDB()) {
                setEmbeddedStorage(false);
            } else {
                boolean embeddedStorage =
                        PropertyUtil.embeddedStorage || Boolean.getBoolean(PropertiesConstant.EMBEDDED_STORAGE);
                setEmbeddedStorage(embeddedStorage);
                
                // If the embedded data source storage is not turned on, it is automatically
                // upgraded to the external data source storage, as before
                if (!embeddedStorage) {
                    setUseExternalDB(true);
                }
            }
        } catch (Exception e) {
            LOGGER.error("read application.properties failed", e);
            throw e;
        }
    }
    
    private boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
    }
    
    private int getInt(String key, int defaultValue) {
        return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
    }
    
    private String getString(String key, String defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        LOGGER.info("{}:{}", key, value);
        return value;
    }
    
    public String getProperty(String key) {
        return EnvUtil.getProperty(key);
    }
    
    public String getProperty(String key, String defaultValue) {
        return EnvUtil.getProperty(key, defaultValue);
    }
    
    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        loadSetting();
    }
}
