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

import com.alibaba.nacos.core.utils.ApplicationUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * properties utils
 *
 * @author Nacos
 */
public class PropertyUtil implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private final static Logger logger = LogUtil.defaultLog;

    private static int notifyConnectTimeout = 100;
    private static int notifySocketTimeout = 200;
    private static int maxHealthCheckFailCount = 12;
    private static boolean isHealthCheck = true;
    private static int maxContent = 10 * 1024 * 1024;

    /**
     * 是否开启容量管理
     */
    private static boolean isManageCapacity = true;
    /**
     * 是否开启容量管理的限制检验功能，包括配置个数上限、配置内容大小限制等
     */
    private static boolean isCapacityLimitCheck = false;
    /**
     * 集群默认容量上限
     */
    private static int defaultClusterQuota = 100000;
    /**
     * 每个Group默认容量上限
     */
    private static int defaultGroupQuota = 200;
    /**
     * 每个Tenant默认容量上限
     */
    private static int defaultTenantQuota = 200;
    /**
     * 单个配置中content的最大大小，单位为字节
     */
    private static int defaultMaxSize = 100 * 1024;
    /**
     * 聚合数据子配置最大个数
     */
    private static int defaultMaxAggrCount = 10000;
    /**
     * 聚合数据单个子配置中content的最大大小，单位为字节
     */
    private static int defaultMaxAggrSize = 1024;
    /**
     * 初始化容量信息记录时，发现已经到达限额时的扩容百分比
     */
    private static int initialExpansionPercent = 100;
    /**
     * 修正容量信息表使用量（usage）的时间间隔，单位为秒
     */
    private static int correctUsageDelay = 10 * 60;
    /**
     * 单机模式使用db
     */
    private static boolean useExternalDB = false;
    /**
     * 内嵌存储 value = ${nacos.standalone}
     */
    private static boolean embeddedStorage = ApplicationUtils.getStandaloneMode();

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
        return ApplicationUtils.getStandaloneMode();
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
        return ApplicationUtils.getStandaloneMode() && isEmbeddedStorage();
    }

    public static void setEmbeddedStorage(boolean embeddedStorage) {
        PropertyUtil.embeddedStorage = embeddedStorage;
    }

    private void loadSetting() {
        try {
            setNotifyConnectTimeout(Integer.parseInt(ApplicationUtils.getProperty("notifyConnectTimeout", "100")));
            logger.info("notifyConnectTimeout:{}", notifyConnectTimeout);
            setNotifySocketTimeout(Integer.parseInt(ApplicationUtils.getProperty("notifySocketTimeout", "200")));
            logger.info("notifySocketTimeout:{}", notifySocketTimeout);
            setHealthCheck(Boolean.parseBoolean(ApplicationUtils.getProperty("isHealthCheck", "true")));
            logger.info("isHealthCheck:{}", isHealthCheck);
            setMaxHealthCheckFailCount(Integer.parseInt(ApplicationUtils.getProperty("maxHealthCheckFailCount", "12")));
            logger.info("maxHealthCheckFailCount:{}", maxHealthCheckFailCount);
            setMaxContent(Integer.parseInt(ApplicationUtils.getProperty("maxContent", String.valueOf(maxContent))));
            logger.info("maxContent:{}", maxContent);
            // 容量管理
            setManageCapacity(getBoolean("isManageCapacity", isManageCapacity));
            setCapacityLimitCheck(getBoolean("isCapacityLimitCheck", isCapacityLimitCheck));
            setDefaultClusterQuota(getInt("defaultClusterQuota", defaultClusterQuota));
            setDefaultGroupQuota(getInt("defaultGroupQuota", defaultGroupQuota));
            setDefaultTenantQuota(getInt("defaultTenantQuota", defaultTenantQuota));
            setDefaultMaxSize(getInt("defaultMaxSize", defaultMaxSize));
            setDefaultMaxAggrCount(getInt("defaultMaxAggrCount", defaultMaxAggrCount));
            setDefaultMaxAggrSize(getInt("defaultMaxAggrSize", defaultMaxAggrSize));
            setCorrectUsageDelay(getInt("correctUsageDelay", correctUsageDelay));
            setInitialExpansionPercent(getInt("initialExpansionPercent", initialExpansionPercent));

            // External data sources are used by default in cluster mode
            setUseExternalDB("mysql".equalsIgnoreCase(getString("spring.datasource.platform", "")));

            // must initialize after setUseExternalDB
            // This value is true in stand-alone mode and false in cluster mode
            // If this value is set to true in cluster mode, nacos's distributed storage engine is turned on
            // default value is depend on ${nacos.standalone}

            if (isUseExternalDB()) {
                setEmbeddedStorage(false);
            } else {
                boolean embeddedStorage = PropertyUtil.embeddedStorage || Boolean.getBoolean("embeddedStorage");
                setEmbeddedStorage(embeddedStorage);

                // If the embedded data source storage is not turned on, it is automatically
                // upgraded to the external data source storage, as before
                if (!embeddedStorage) {
                    setUseExternalDB(true);
                }
            }
        } catch (Exception e) {
            logger.error("read application.properties failed", e);
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
        logger.info("{}:{}", key, value);
        return value;
    }

    public String getProperty(String key) {
        return ApplicationUtils.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return ApplicationUtils.getProperty(key, defaultValue);
    }

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        ApplicationUtils.injectEnvironment(configurableApplicationContext.getEnvironment());
        loadSetting();
    }
}
