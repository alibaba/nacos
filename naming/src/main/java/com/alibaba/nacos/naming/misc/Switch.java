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
package com.alibaba.nacos.naming.misc;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.naming.raft.RaftCore;
import com.alibaba.nacos.naming.raft.RaftListener;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author nacos
 */
public class Switch {
    private static volatile SwitchDomain dom = new SwitchDomain();
    private static boolean enableService = false;

    public static long getClientBeatInterval() {
        return dom.getClientBeatInterval();
    }

    public static void setClientBeatInterval(long clientBeatInterval) {
        dom.setClientBeatInterval(clientBeatInterval);
    }


    static {

        Loggers.RAFT.info("Switch init start!");

        RaftCore.listen(new RaftListener() {
            @Override
            public boolean interests(String key) {
                return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE + UtilsAndCommons.SWITCH_DOMAIN_NAME);
            }

            @Override
            public boolean matchUnlistenKey(String key) {
                return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID_PRE + UtilsAndCommons.SWITCH_DOMAIN_NAME);
            }

            @Override
            public void onChange(String key, String value) throws Exception {
                Loggers.RAFT.info("[NACOS-RAFT] datum is changed, key: {}, value: {}", key, value);
                if (StringUtils.isEmpty(value)) {
                    return;
                }
                dom = JSON.parseObject(value, new TypeReference<SwitchDomain>() {
                });
            }

            @Override
            public void onDelete(String key, String value) throws Exception {

            }
        });
    }

    public static long getPushCacheMillis(String dom) {
        if (Switch.dom.pushCacheMillisMap == null
            || !Switch.dom.pushCacheMillisMap.containsKey(dom)) {
            return Switch.dom.defaultPushCacheMillis;
        }

        return Switch.dom.pushCacheMillisMap.get(dom);
    }

    public static long getPushCacheMillis() {
        return Switch.dom.defaultPushCacheMillis;
    }

    public static long getCacheMillis(String dom) {
        if (Switch.dom.cacheMillisMap == null
            || !Switch.dom.cacheMillisMap.containsKey(dom)) {
            return Switch.dom.defaultCacheMillis;
        }

        return Switch.dom.cacheMillisMap.get(dom);
    }

    public static long getCacheMillis() {
        return Switch.dom.defaultCacheMillis;
    }

    public static void setPushCacheMillis(String dom, Long cacheMillis) {
        if (StringUtils.isEmpty(dom)) {
            Switch.dom.defaultPushCacheMillis = cacheMillis;
        } else {
            Switch.dom.pushCacheMillisMap.put(dom, cacheMillis);
        }
    }

    public static void setCacheMillis(String dom, long cacheMillis) {
        if (StringUtils.isEmpty(dom)) {
            Switch.dom.defaultCacheMillis = cacheMillis;
        } else {
            Switch.dom.cacheMillisMap.put(dom, cacheMillis);
        }
    }

    public static SwitchDomain getDom() {
        return dom;
    }

    public static List<String> getMasters() {
        return dom.masters;
    }

    public static void setMasters(List<String> masters) {
        dom.masters = masters;
    }

    public static void setDom(SwitchDomain dom) {
        Switch.dom = dom;
    }

    public static void save() {
        try {
            RaftCore.doSignalPublish(UtilsAndCommons.getDomStoreKey(dom), JSON.toJSONString(dom), true);
        } catch (Exception e) {
            Loggers.SRV_LOG.error("[SWITCH] failed to save switch", e);
        }
    }

    public static SwitchDomain.HttpHealthParams getHttpHealthParams() {
        return dom.httpHealthParams;
    }

    public static SwitchDomain.MysqlHealthParams getMysqlHealthParams() {
        return dom.mysqlHealthParams;
    }

    public static SwitchDomain.TcpHealthParams getTcpHealthParams() {
        return dom.tcpHealthParams;
    }

    public static boolean isHealthCheckEnabled() {
        return Switch.dom.healthCheckEnabled;
    }

    public static boolean isHealthCheckEnabled(String dom) {
        return Switch.dom.healthCheckEnabled || Switch.dom.getHealthCheckWhiteList().contains(dom);
    }

    public static void setHeathCheckEnabled(boolean enabled) {
        Switch.dom.healthCheckEnabled = enabled;
    }

    public static String getDefaultHealthCheckMode() {
        return Switch.dom.defaultHealthCheckMode;
    }

    public static void setDefaultHealthCheckMode(String healthCheckMode) {
        Switch.dom.defaultHealthCheckMode = healthCheckMode;
    }

    public static boolean isEnableAuthentication() {
        return dom.isEnableAuthentication();
    }

    public static void setEnableAuthentication(boolean enableAuthentication) {
        dom.setEnableAuthentication(enableAuthentication);
    }

    public static boolean isDistroEnabled() {
        return Switch.dom.distroEnabled;
    }

    public static void setDistroEnabled(boolean enabled) {
        Switch.dom.distroEnabled = enabled;
    }

    public static void setDistroThreshold(float distroThreshold) {
        dom.distroThreshold = distroThreshold;
    }

    public static float getDistroThreshold() {
        return dom.distroThreshold;
    }

    public static Integer getAdWeight(String ip) {
        if (dom.adWeightMap == null
            || !dom.adWeightMap.containsKey(ip)) {
            return 0;
        }

        return dom.adWeightMap.get(ip);
    }

    public static void setAdWeight(String ip, int weight) {
        dom.adWeightMap.put(ip, weight);
    }

    public static String getPushJavaVersion() {
        return dom.pushJavaVersion;
    }

    public static String getPushGoVersion() {
        return dom.pushGoVersion;
    }

    public static String getPushPythonVersion() {
        return dom.pushPythonVersion;
    }

    public static String getPushCVersion() {
        return dom.pushCVersion;
    }

    public static void setPushJavaVersion(String pushJavaVersion) {
        dom.pushJavaVersion = pushJavaVersion;
    }

    public static void setPushGoVersion(String pushGoVersion) {
        dom.pushGoVersion = pushGoVersion;
    }

    public static void setPushPythonVersion(String pushPythonVersion) {
        dom.pushPythonVersion = pushPythonVersion;
    }

    public static void setPushCVersion(String pushCVersion) {
        dom.pushCVersion = pushCVersion;
    }

    public static int getCheckTimes() {
        return dom.checkTimes;
    }

    public static void setCheckTimes(int times) {
        dom.checkTimes = times;
    }

    public static long getdistroServerExpiredMillis() {
        return dom.distroServerExpiredMillis;
    }

    public static long getServerStatusSynchronizationPeriodMillis() {
        return dom.serverStatusSynchronizationPeriodMillis;
    }

    public static void setServerStatusSynchronizationPeriodMillis(long serverStatusSynchronizationPeriodMillis) {
        dom.serverStatusSynchronizationPeriodMillis = serverStatusSynchronizationPeriodMillis;
    }

    public static long getDomStatusSynchronizationPeriodMillis() {
        return dom.domStatusSynchronizationPeriodMillis;
    }

    public static void setDomStatusSynchronizationPeriodMillis(long domStatusSynchronizationPeriodMillis) {
        dom.domStatusSynchronizationPeriodMillis = domStatusSynchronizationPeriodMillis;
    }

    public static boolean getDisableAddIP() {
        return dom.disableAddIP;
    }

    public static void setDisableAddIP(boolean enable) {
        dom.disableAddIP = enable;
    }

    public static boolean getEnableCache() {
        return dom.enableCache;
    }

    public static void setEnableCache(boolean enableCache) {
        dom.enableCache = enableCache;
    }

    public static Map<String, Integer> getLimitedUrlMap() {
        return dom.limitedUrlMap;
    }

    public static void setLimitedUrlMap(Map<String, Integer> limitedUrlMap) {
        dom.limitedUrlMap = limitedUrlMap;
    }

    public static void setTrafficSchedulingJavaVersion(String version) {
        dom.trafficSchedulingJavaVersion = version;
    }

    public static String getTrafficSchedulingJavaVersion() {
        return dom.trafficSchedulingJavaVersion;
    }

    public static void setTrafficSchedulingPythonVersion(String version) {
        dom.trafficSchedulingPythonVersion = version;
    }

    public static String getTrafficSchedulingPythonVersion() {
        return dom.trafficSchedulingPythonVersion;
    }

    public static void setTrafficSchedulingCVersion(String version) {
        dom.trafficSchedulingCVersion = version;
    }

    public static String getTrafficSchedulingCVersion() {
        return dom.trafficSchedulingCVersion;
    }

    public static void setTrafficSchedulingTengineVersion(String version) {
        dom.trafficSchedulingTengineVersion = version;
    }

    public static String getTrafficSchedulingTengineVersion() {
        return dom.trafficSchedulingTengineVersion;
    }


    public static boolean isSendBeatOnly() {
        return dom.isSendBeatOnly();
    }

    public static void setSendBeatOnly(boolean sentBeatOnly) {
        dom.setSendBeatOnly(sentBeatOnly);
    }

    public static boolean isEnableStandalone() {
        return dom.isEnableStandalone();
    }

    public static void setEnableStandalone(boolean enableStandalone) {
        dom.setEnableStandalone(enableStandalone);
    }

    public static Set<String> getHealthCheckWhiteList() {
        return dom.getHealthCheckWhiteList();
    }

    public static void setHealthCheckWhiteList(Set<String> healthCheckWhiteList) {
        dom.setHealthCheckWhiteList(healthCheckWhiteList);
    }

    public static List<String> getIncrementalList() {
        return dom.getIncrementalList();
    }

    public static boolean isAllDomNameCache() {
        return dom.isAllDomNameCache();
    }

    public static void setAllDomNameCache(boolean enable) {
        dom.setAllDomNameCache(enable);
    }

}
