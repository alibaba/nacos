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
package com.alibaba.nacos.api.naming.pojo;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author dungu.zpf
 */
public class ServiceInfo {

    @JSONField(serialize = false)
    private String jsonFromServer = EMPTY;
    public static final String SPLITER = "@@";

    @JSONField(name = "dom")
    private String name;

    private String clusters;

    private long cacheMillis = 1000L;

    @JSONField(name = "hosts")
    private List<Instance> hosts = new ArrayList<Instance>();

    private long lastRefTime = 0L;

    private String checksum = "";

    private String env = "";

    private volatile boolean allIPs = false;

    public ServiceInfo() {
    }

    public boolean isAllIPs() {
        return allIPs;
    }

    public void setAllIPs(boolean allIPs) {
        this.allIPs = allIPs;
    }

    public ServiceInfo(String key) {

        int maxKeySectionCount = 4;
        int allIpFlagIndex = 3;
        int envIndex = 2;
        int clusterIndex = 1;
        int serviceNameIndex = 0;

        String[] keys = key.split(SPLITER);
        if (keys.length >= maxKeySectionCount) {
            this.name = keys[serviceNameIndex];
            this.clusters = keys[clusterIndex];
            this.env = keys[envIndex];
            if (strEquals(keys[allIpFlagIndex], ALL_IPS)) {
                this.setAllIPs(true);
            }
        } else if (keys.length >= allIpFlagIndex) {
            this.name = keys[serviceNameIndex];
            this.clusters = keys[clusterIndex];
            if (strEquals(keys[envIndex], ALL_IPS)) {
                this.setAllIPs(true);
            } else {
                this.env = keys[envIndex];
            }
        } else if (keys.length >= envIndex) {
            this.name = keys[serviceNameIndex];
            if (strEquals(keys[clusterIndex], ALL_IPS)) {
                this.setAllIPs(true);
            } else {
                this.clusters = keys[clusterIndex];
            }
        }

        this.name = keys[0];
    }

    public ServiceInfo(String name, String clusters) {
        this(name, clusters, EMPTY);
    }

    public ServiceInfo(String name, String clusters, String env) {
        this.name = name;
        this.clusters = clusters;
        this.env = env;
    }

    public int ipCount() {
        return hosts.size();
    }

    public boolean expired() {
        return System.currentTimeMillis() - lastRefTime > cacheMillis;
    }

    public void setHosts(List<Instance> hosts) {
        this.hosts = hosts;
    }

    public boolean isValid() {
        return hosts != null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setLastRefTime(long lastRefTime) {
        this.lastRefTime = lastRefTime;
    }

    public long getLastRefTime() {
        return lastRefTime;
    }

    public String getClusters() {
        return clusters;
    }

    public void setClusters(String clusters) {
        this.clusters = clusters;
    }

    public long getCacheMillis() {
        return cacheMillis;
    }

    public void setCacheMillis(long cacheMillis) {
        this.cacheMillis = cacheMillis;
    }

    public List<Instance> getHosts() {

        return new ArrayList<Instance>(hosts);
    }

    public boolean validate() {
        if (isAllIPs()) {
            return true;
        }

        List<Instance> validHosts = new ArrayList<Instance>();
        for (Instance host : hosts) {
            if (!host.isHealthy()) {
                continue;
            }

            for (int i = 0; i < host.getWeight(); i++) {
                validHosts.add(host);
            }
        }

        return true;
    }

    @JSONField(serialize = false)
    public String getJsonFromServer() {
        return jsonFromServer;
    }

    public void setJsonFromServer(String jsonFromServer) {
        this.jsonFromServer = jsonFromServer;
    }

    @JSONField(serialize = false)
    public String getKey() {
        return getKey(name, clusters, env, isAllIPs());
    }

    @JSONField(serialize = false)
    public static String getKey(String name, String clusters, String unit) {
        return getKey(name, clusters, unit, false);
    }

    @JSONField(serialize = false)
    public static String getKey(String name, String clusters, String unit, boolean isAllIPs) {

        if (isEmpty(unit)) {
            unit = EMPTY;
        }

        if (!isEmpty(clusters) && !isEmpty(unit)) {
            return isAllIPs ? name + SPLITER + clusters + SPLITER + unit + SPLITER + ALL_IPS
                    : name + SPLITER + clusters + SPLITER + unit;
        }

        if (!isEmpty(clusters)) {
            return isAllIPs ? name + SPLITER + clusters + SPLITER + ALL_IPS : name + SPLITER + clusters;
        }

        if (!isEmpty(unit)) {
            return isAllIPs ? name + SPLITER + EMPTY + SPLITER + unit + SPLITER + ALL_IPS :
                    name + SPLITER + EMPTY + SPLITER + unit;
        }

        return isAllIPs ? name + SPLITER + ALL_IPS : name;
    }

    @Override
    public String toString() {
        return getKey();
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }

    private static boolean strEquals(String str1, String str2) {
        return str1 == null ? str2 == null : str1.equals(str2);
    }

    private static boolean isEmpty(Collection coll) {
        return (coll == null || coll.isEmpty());
    }

    private static final String EMPTY = "";

    private static final String ALL_IPS = "000--00-ALL_IPS--00--000";
}
