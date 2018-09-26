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
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.core.Domain;
import com.alibaba.nacos.naming.core.IpAddress;
import com.alibaba.nacos.naming.raft.RaftListener;
import org.apache.commons.lang3.StringUtils;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author nacos
 */
public class SwitchDomain implements Domain, RaftListener {
    public String name = "00-00---000-VIPSRV_SWITCH_DOMAIN-000---00-00";

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
    public String pushJavaVersion = "0.1.0";
    public String pushPythonVersion = "0.4.3";
    public String pushCVersion = "1.0.12";
    public String trafficSchedulingJavaVersion = "4.5.0";
    public String trafficSchedulingPythonVersion = "9999.0.0";
    public String trafficSchedulingCVersion = "1.0.5";
    public String trafficSchedulingTengineVersion = "2.0.0";

    public boolean enableAuthentication = false;

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

    @Override
    public String getToken() {
        return token;
    }

    @Override
    public void setToken(String token) {
        this.token = token;
    }

    @Override
    public List<String> getOwners() {
        return masters;
    }

    public boolean isSendBeatOnly() {
        return sendBeatOnly;
    }

    public void setSendBeatOnly(boolean sendBeatOnly) {
        this.sendBeatOnly = sendBeatOnly;
    }

    @Override
    public void setOwners(List<String> owners) {
        this.masters = owners;
    }

    // the followings are not implemented

    @Override
    public String getName() {
        return "00-00---000-VIPSRV_SWITCH_DOMAIN-000---00-00";
    }

    @Override
    public void setName(String name) {

    }

    @Override
    public void init() {

    }

    @Override
    public void destroy() throws Exception {

    }

    @Override
    public List<IpAddress> allIPs() {
        return null;
    }

    @Override
    public List<IpAddress> srvIPs(String clientIp) {
        return null;
    }

    public String toJSON() {
        return JSON.toJSONString(this);
    }

    @Override
    public void setProtectThreshold(float protectThreshold) {

    }

    @Override
    public float getProtectThreshold() {
        return 0;
    }

    @Override
    public void update(Domain dom) {

    }

    @Override
    @JSONField(serialize = false)
    public String getChecksum() {
        throw new NotImplementedException();
    }

    @Override
    public void recalculateChecksum() {
        throw new NotImplementedException();
    }


    @Override
    public boolean interests(String key) {
        return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID + "." + name);
    }

    @Override
    public boolean matchUnlistenKey(String key) {
        return StringUtils.equals(key, UtilsAndCommons.DOMAINS_DATA_ID + "." + name);
    }

    @Override
    public void onChange(String key, String value) throws Exception {
        SwitchDomain domain = JSON.parseObject(value, SwitchDomain.class);
        update(domain);
    }

    @Override
    public void onDelete(String key, String value) throws Exception {

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
