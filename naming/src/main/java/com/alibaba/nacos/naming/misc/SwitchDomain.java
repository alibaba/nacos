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

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.consistency.DataListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author nacos
 */
@Component
public class SwitchDomain implements DataListener<SwitchDomain> {

    public String name = UtilsAndCommons.SWITCH_DOMAIN_NAME;

    public List<String> masters;

    public Map<String, Integer> adWeightMap = new HashMap<String, Integer>();

    public long defaultPushCacheMillis = TimeUnit.SECONDS.toMillis(10);

    private long clientBeatInterval = 5 * 1000;

    public long defaultCacheMillis = 10000L;

    public float distroThreshold = 0.7F;

    public String token = UtilsAndCommons.SUPER_TOKEN;

    public Map<String, Long> cacheMillisMap = new HashMap<String, Long>();

    public Map<String, Long> pushCacheMillisMap = new HashMap<String, Long>();

    public boolean healthCheckEnabled = true;

    public boolean distroEnabled = true;

    public boolean enableStandalone = true;

    public int checkTimes = 3;

    public HttpHealthParams httpHealthParams = new HttpHealthParams();

    public TcpHealthParams tcpHealthParams = new TcpHealthParams();

    public MysqlHealthParams mysqlHealthParams = new MysqlHealthParams();

    private List<String> incrementalList = new ArrayList<>();

    private boolean allDomNameCache = true;

    public long serverStatusSynchronizationPeriodMillis = TimeUnit.SECONDS.toMillis(15);

    public long domStatusSynchronizationPeriodMillis = TimeUnit.SECONDS.toMillis(5);

    public boolean disableAddIP = false;

    public boolean enableCache = true;

    public boolean sendBeatOnly = false;

    public Map<String, Integer> limitedUrlMap = new HashMap<>();

    /**
     * The server is regarded as expired if its two reporting interval is lagger than this variable.
     */
    public long distroServerExpiredMillis = 30000;

    /**
     * since which version, push can be enabled
     */
    public String pushGoVersion = "0.1.0";
    public String pushJavaVersion = "0.1.0";
    public String pushPythonVersion = "0.4.3";
    public String pushCVersion = "1.0.12";

    public boolean enableAuthentication = false;

    public String overriddenServerStatus = null;

    public boolean isEnableAuthentication() {
        return enableAuthentication;
    }

    public void setEnableAuthentication(boolean enableAuthentication) {
        this.enableAuthentication = enableAuthentication;
    }

    public Set<String> getHealthCheckWhiteList() {
        return healthCheckWhiteList;
    }

    public void setHealthCheckWhiteList(Set<String> healthCheckWhiteList) {
        this.healthCheckWhiteList = healthCheckWhiteList;
    }

    private Set<String> healthCheckWhiteList = new HashSet<>();

    public long getClientBeatInterval() {
        return clientBeatInterval;
    }

    public void setClientBeatInterval(long clientBeatInterval) {
        this.clientBeatInterval = clientBeatInterval;
    }

    public boolean isEnableCache() {
        return enableCache;
    }

    public boolean isEnableStandalone() {
        return enableStandalone;
    }

    public void setEnableStandalone(boolean enableStandalone) {
        this.enableStandalone = enableStandalone;
    }

    public SwitchDomain() {
    }

    public boolean isSendBeatOnly() {
        return sendBeatOnly;
    }

    public void setSendBeatOnly(boolean sendBeatOnly) {
        this.sendBeatOnly = sendBeatOnly;
    }

    // the followings are not implemented

    public String getName() {
        return "00-00---000-VIPSRV_SWITCH_DOMAIN-000---00-00";
    }


    public void update(SwitchDomain dom) {

    }

    public List<String> getIncrementalList() {
        return incrementalList;
    }

    public boolean isAllDomNameCache() {
        return allDomNameCache;
    }

    public void setAllDomNameCache(boolean enable) {
        allDomNameCache = enable;
    }

    public List<String> getMasters() {
        return masters;
    }

    public void setMasters(List<String> masters) {
        this.masters = masters;
    }

    public Map<String, Integer> getAdWeightMap() {
        return adWeightMap;
    }

    public void setAdWeightMap(Map<String, Integer> adWeightMap) {
        this.adWeightMap = adWeightMap;
    }

    public Integer getAdWeight(String key) {
        return getAdWeightMap().get(key);
    }

    public long getDefaultPushCacheMillis() {
        return defaultPushCacheMillis;
    }

    public void setDefaultPushCacheMillis(long defaultPushCacheMillis) {
        this.defaultPushCacheMillis = defaultPushCacheMillis;
    }

    public long getDefaultCacheMillis() {
        return defaultCacheMillis;
    }

    public void setDefaultCacheMillis(long defaultCacheMillis) {
        this.defaultCacheMillis = defaultCacheMillis;
    }

    public float getDistroThreshold() {
        return distroThreshold;
    }

    public void setDistroThreshold(float distroThreshold) {
        this.distroThreshold = distroThreshold;
    }

    public long getPushCacheMillis(String dom) {
        if (pushCacheMillisMap == null
            || !pushCacheMillisMap.containsKey(dom)) {
            return defaultPushCacheMillis;
        }

        return pushCacheMillisMap.get(dom);
    }

    @JSONField(serialize = false)
    public void setPushCacheMillis(Long cacheMillis) {
        defaultPushCacheMillis = cacheMillis;
    }

    public boolean isHealthCheckEnabled() {
        return healthCheckEnabled;
    }

    public void setHealthCheckEnabled(boolean healthCheckEnabled) {
        this.healthCheckEnabled = healthCheckEnabled;
    }

    public boolean isHealthCheckEnabled(String dom) {
        return healthCheckEnabled || getHealthCheckWhiteList().contains(dom);
    }

    public boolean isDistroEnabled() {
        return distroEnabled;
    }

    public void setDistroEnabled(boolean distroEnabled) {
        this.distroEnabled = distroEnabled;
    }

    public int getCheckTimes() {
        return checkTimes;
    }

    public void setCheckTimes(int checkTimes) {
        this.checkTimes = checkTimes;
    }

    public HttpHealthParams getHttpHealthParams() {
        return httpHealthParams;
    }

    public void setHttpHealthParams(HttpHealthParams httpHealthParams) {
        this.httpHealthParams = httpHealthParams;
    }

    public TcpHealthParams getTcpHealthParams() {
        return tcpHealthParams;
    }

    public void setTcpHealthParams(TcpHealthParams tcpHealthParams) {
        this.tcpHealthParams = tcpHealthParams;
    }

    public MysqlHealthParams getMysqlHealthParams() {
        return mysqlHealthParams;
    }

    public void setMysqlHealthParams(MysqlHealthParams mysqlHealthParams) {
        this.mysqlHealthParams = mysqlHealthParams;
    }

    public long getServerStatusSynchronizationPeriodMillis() {
        return serverStatusSynchronizationPeriodMillis;
    }

    public void setServerStatusSynchronizationPeriodMillis(long serverStatusSynchronizationPeriodMillis) {
        this.serverStatusSynchronizationPeriodMillis = serverStatusSynchronizationPeriodMillis;
    }

    public long getDomStatusSynchronizationPeriodMillis() {
        return domStatusSynchronizationPeriodMillis;
    }

    public void setDomStatusSynchronizationPeriodMillis(long domStatusSynchronizationPeriodMillis) {
        this.domStatusSynchronizationPeriodMillis = domStatusSynchronizationPeriodMillis;
    }

    public boolean isDisableAddIP() {
        return disableAddIP;
    }

    public void setDisableAddIP(boolean disableAddIP) {
        this.disableAddIP = disableAddIP;
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    public Map<String, Integer> getLimitedUrlMap() {
        return limitedUrlMap;
    }

    public void setLimitedUrlMap(Map<String, Integer> limitedUrlMap) {
        this.limitedUrlMap = limitedUrlMap;
    }

    public long getDistroServerExpiredMillis() {
        return distroServerExpiredMillis;
    }

    public void setDistroServerExpiredMillis(long distroServerExpiredMillis) {
        this.distroServerExpiredMillis = distroServerExpiredMillis;
    }

    public String getPushGoVersion() {
        return pushGoVersion;
    }

    public void setPushGoVersion(String pushGoVersion) {
        this.pushGoVersion = pushGoVersion;
    }

    public String getPushJavaVersion() {
        return pushJavaVersion;
    }

    public void setPushJavaVersion(String pushJavaVersion) {
        this.pushJavaVersion = pushJavaVersion;
    }

    public String getPushPythonVersion() {
        return pushPythonVersion;
    }

    public void setPushPythonVersion(String pushPythonVersion) {
        this.pushPythonVersion = pushPythonVersion;
    }

    public String getPushCVersion() {
        return pushCVersion;
    }

    public void setPushCVersion(String pushCVersion) {
        this.pushCVersion = pushCVersion;
    }

    public String getOverriddenServerStatus() {
        return overriddenServerStatus;
    }

    public void setOverriddenServerStatus(String overriddenServerStatus) {
        this.overriddenServerStatus = overriddenServerStatus;
    }

    public void replace(SwitchDomain newSwitchDomain) {
        // TODO
    }

    @Override
    public boolean interests(String key) {
        return key.contains(UtilsAndCommons.SWITCH_DOMAIN_NAME);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return key.contains(UtilsAndCommons.SWITCH_DOMAIN_NAME);
    }

    @Override
    public void onChange(String key, SwitchDomain domain) throws Exception {
        update(domain);
    }

    @Override
    public void onDelete(String key) throws Exception {

    }

    public interface HealthParams {
        /**
         * Maximum RT
         *
         * @return Max RT
         */
        int getMax();

        /**
         * Minimum RT
         *
         * @return Minimum RT
         */
        int getMin();

        /**
         * Get Factor to reevaluate RT
         *
         * @return reevaluate factor
         */
        float getFactor();
    }

    public static class HttpHealthParams implements HealthParams {

        public static final int MIN_MAX = 3000;
        public static final int MIN_MIN = 500;

        private int max = 5000;
        private int min = 500;
        private float factor = 0.85F;

        @Override
        public int getMax() {
            return max;
        }

        @Override
        public int getMin() {
            return min;
        }

        @Override
        public float getFactor() {
            return factor;
        }

        public void setFactor(float factor) {
            this.factor = factor;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public void setMin(int min) {
            this.min = min;
        }
    }

    public static class MysqlHealthParams implements HealthParams {
        private int max = 3000;
        private int min = 2000;
        private float factor = 0.65F;

        @Override
        public int getMax() {
            return max;
        }

        @Override
        public int getMin() {
            return min;
        }

        @Override
        public float getFactor() {
            return factor;
        }

        public void setFactor(float factor) {
            this.factor = factor;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public void setMin(int min) {
            this.min = min;
        }
    }

    public static class TcpHealthParams implements HealthParams {
        private int max = 5000;
        private int min = 1000;
        private float factor = 0.75F;

        @Override
        public int getMax() {
            return max;
        }

        @Override
        public int getMin() {
            return min;
        }

        @Override
        public float getFactor() {
            return factor;
        }

        public void setFactor(float factor) {
            this.factor = factor;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public void setMin(int min) {
            this.min = min;
        }
    }
}
