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

import org.slf4j.Logger;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static com.alibaba.nacos.core.utils.SystemUtils.STANDALONE_MODE;

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
    private static boolean useMysql = false;
    /**
     * 内嵌分布式存储
     */
    private static boolean embeddedDistributedStorage = false;

    private Environment env;

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
        return STANDALONE_MODE;
    }

    public static boolean isUseMysql() {
        return useMysql;
    }

    public static void setUseMysql(boolean useMysql) {
        PropertyUtil.useMysql = useMysql;
    }

    public static boolean isEmbeddedDistributedStorage() {
        return embeddedDistributedStorage;
    }

    public static void setEmbeddedDistributedStorage(boolean embeddedDistributedStorage) {
        PropertyUtil.embeddedDistributedStorage = embeddedDistributedStorage && !isUseMysql();
    }

    public static boolean isEnableDistributedID() {
        return !STANDALONE_MODE && isEmbeddedDistributedStorage();
    }

    private void loadSetting() {
        try {
            setNotifyConnectTimeout(Integer.parseInt(env.getProperty("notifyConnectTimeout", "100")));
            logger.info("notifyConnectTimeout:{}", notifyConnectTimeout);
            setNotifySocketTimeout(Integer.parseInt(env.getProperty("notifySocketTimeout", "200")));
            logger.info("notifySocketTimeout:{}", notifySocketTimeout);
            setHealthCheck(Boolean.parseBoolean(env.getProperty("isHealthCheck", "true")));
            logger.info("isHealthCheck:{}", isHealthCheck);
            setMaxHealthCheckFailCount(Integer.parseInt(env.getProperty("maxHealthCheckFailCount", "12")));
            logger.info("maxHealthCheckFailCount:{}", maxHealthCheckFailCount);
            setMaxContent(Integer.parseInt(env.getProperty("maxContent", String.valueOf(maxContent))));
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
            setUseMysql(getString("spring.datasource.platform", "").equals("mysql"));
            setEmbeddedDistributedStorage(getBoolean("embeddedDistributedStorage", embeddedDistributedStorage));
        } catch (Exception e) {
            logger.error("read application.properties failed", e);
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        return Boolean.parseBoolean(getString(key, String.valueOf(defaultValue)));
    }

    private int getInt(String key, int defaultValue) {
        return Integer.parseInt(getString(key, String.valueOf(defaultValue)));
    }

    private String getString(String key, String defaultValue) {
        String value = env.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        logger.info("{}:{}", key, value);
        return value;
    }

    public String getProperty(String key) {
        return env.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return env.getProperty(key, defaultValue);
    }

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        env = configurableApplicationContext.getEnvironment();
        loadSetting();
    }
}
