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
package com.alibaba.nacos.naming.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP under domain
 *
 * @author dungu.zpf
 */
public class IpAddress implements Comparable {

    private static final double MAX_WEIGHT_VALUE = 10000.0D;
    private static final double MIN_POSTIVE_WEIGHT_VALUE = 0.01D;
    private static final double MIN_WEIGHT_VALUE = 0.00D;

    private String ip;
    private int port = 0;
    private double weight = 1.0;
    private String clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
    private volatile long lastBeat = System.currentTimeMillis();

    @JSONField(serialize = false)
    private String invalidType = InvalidType.VALID;

    public static class InvalidType {
        public final static String HTTP_404 = "404";
        public final static String WEIGHT_0 = "weight_0";
        public final static String NORMAL_INVALID = "invalid";
        public final static String VALID = "valid";
    }

    public String getInvalidType() {
        return invalidType;
    }

    public void setInvalidType(String invalidType) {
        this.invalidType = invalidType;
    }

    @JSONField(serialize = false)
    private Cluster cluster;

    private volatile boolean valid = true;

    @JSONField(serialize = false)
    private volatile boolean mockValid = false;

    @JSONField(serialize = false)
    private volatile boolean preValid = true;

    private volatile boolean marked = false;

    private String tenant;

    private String app;

    private Map<String, String> metadata = new ConcurrentHashMap<>();

    public static final Pattern IP_PATTERN
            = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):?(\\d{1,5})?");

    public static final String SPLITER = "_";

    public IpAddress() {
    }

    public boolean isMockValid() {
        return mockValid;
    }

    public void setMockValid(boolean mockValid) {
        this.mockValid = mockValid;
    }

    public long getLastBeat() {
        return lastBeat;
    }

    public void setLastBeat(long lastBeat) {
        this.lastBeat = lastBeat;
    }

    public IpAddress(String ip, int port) {
        this.ip = ip.trim();
        this.port = port;
        this.clusterName = UtilsAndCommons.DEFAULT_CLUSTER_NAME;
    }

    public IpAddress(String ip, int port, String clusterName) {
        this.ip = ip.trim();
        this.port = port;
        this.clusterName = clusterName;
    }

    public IpAddress(String ip, int port, String clusterName, String tenant, String app) {
        this.ip = ip.trim();
        this.port = port;
        this.clusterName = clusterName;
        this.tenant = tenant;
        this.app = app;
    }

    public static IpAddress fromString(String config) {
        String[] ipAddressAttributes = config.split(SPLITER);
        if (ipAddressAttributes.length < 1) {
            return null;
        }

        String provider = ipAddressAttributes[0];
        Matcher matcher = IP_PATTERN.matcher(provider);
        if (!matcher.matches()) {
            return null;
        }

        int expectedGroupCount = 2;

        int port = 0;
        if (NumberUtils.isNumber(matcher.group(expectedGroupCount))) {
            port = Integer.parseInt(matcher.group(expectedGroupCount));
        }

        IpAddress ipAddress = new IpAddress(matcher.group(1), port);

        // 7 possible formats of config:
        // ip:port
        // ip:port_weight
        // ip:port_weight_cluster
        // ip:port_weight_valid
        // ip:port_weight_valid_cluster
        // ip:port_weight_valid_marked
        // ip:port_weight_valid_marked_cluster
        int minimumLength = 1;

        if (ipAddressAttributes.length > minimumLength) {
            // determine 'weight':
            ipAddress.setWeight(NumberUtils.toDouble(ipAddressAttributes[minimumLength], 1));
        }

        minimumLength++;

        if (ipAddressAttributes.length > minimumLength) {
            // determine 'valid':
            if (Boolean.TRUE.toString().equals(ipAddressAttributes[minimumLength]) ||
                    Boolean.FALSE.toString().equals(ipAddressAttributes[minimumLength])) {
                ipAddress.setValid(Boolean.parseBoolean(ipAddressAttributes[minimumLength]));
            }

            // determine 'cluster':
            if (!Boolean.TRUE.toString().equals(ipAddressAttributes[ipAddressAttributes.length - 1]) &&
                    !Boolean.FALSE.toString().equals(ipAddressAttributes[ipAddressAttributes.length - 1])) {
                ipAddress.setClusterName(ipAddressAttributes[ipAddressAttributes.length - 1]);
            }
        }

        minimumLength++;

        if (ipAddressAttributes.length > minimumLength) {
            // determine 'marked':
            if (Boolean.TRUE.toString().equals(ipAddressAttributes[minimumLength]) ||
                    Boolean.FALSE.toString().equals(ipAddressAttributes[minimumLength])) {
                ipAddress.setMarked(Boolean.parseBoolean(ipAddressAttributes[minimumLength]));
            }
        }

        return ipAddress;
    }

    public String toIPAddr() {
        return ip + ":" + port;
    }

    @Override
    public String toString() {
        return getDatumKey() + SPLITER + weight + SPLITER + valid + SPLITER + marked + SPLITER + clusterName;
    }

    public String toJSON() {
        return JSON.toJSONString(this);
    }


    public static IpAddress fromJSON(String json) {
        IpAddress ip;

        try {
            ip = JSON.parseObject(json, IpAddress.class);
        } catch (Exception e) {
            ip = fromString(json);
        }

        if (ip == null) {
            throw new IllegalArgumentException("malfomed ip config: " + json);
        }

        if (ip.getWeight() > MAX_WEIGHT_VALUE) {
            ip.setWeight(MAX_WEIGHT_VALUE);
        }

        if (ip.getWeight() < MIN_POSTIVE_WEIGHT_VALUE && ip.getWeight() > MIN_WEIGHT_VALUE) {
            ip.setWeight(MIN_POSTIVE_WEIGHT_VALUE);
        } else if (ip.getWeight() < MIN_WEIGHT_VALUE) {
            ip.setWeight(0.0D);
        }
        return ip;
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        IpAddress other = (IpAddress) obj;

        // 0 means wild
        return ip.equals(other.getIp()) && (port == other.port || port == 0);
    }

    @JSONField(serialize = false)
    public String getDatumKey() {
        if (port > 0) {
            return ip + ":" + port + ":" + DistroMapper.LOCALHOST_SITE;
        } else {
            return ip + ":" + DistroMapper.LOCALHOST_SITE;
        }
    }

    @JSONField(serialize = false)
    public String getDefaultKey() {
        if (port > 0) {
            return ip + ":" + port + ":" + UtilsAndCommons.UNKNOWN_SITE;
        } else {
            return ip + ":" + UtilsAndCommons.UNKNOWN_SITE;
        }
    }

    @Override
    public int hashCode() {
        return ip.hashCode();
    }

    public void setBeingChecked(boolean isBeingChecked) {
        HealthCheckStatus.get(this).isBeingChecked.set(isBeingChecked);
    }

    public boolean markChecking() {
        return HealthCheckStatus.get(this).isBeingChecked.compareAndSet(false, true);
    }

    @JSONField(serialize = false)
    public long getCheckRT() {
        return HealthCheckStatus.get(this).checkRT;
    }

    @JSONField(serialize = false)
    public AtomicInteger getOKCount() {
        return HealthCheckStatus.get(this).checkOKCount;
    }

    @JSONField(serialize = false)
    public AtomicInteger getFailCount() {
        return HealthCheckStatus.get(this).checkFailCount;
    }

    @JSONField(serialize = false)
    public void setCheckRT(long checkRT) {
        HealthCheckStatus.get(this).checkRT = checkRT;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public synchronized void setValid(boolean valid) {
        this.preValid = this.valid;
        this.valid = valid;
    }

    public boolean isValid() {
        return valid;
    }

    public boolean isPreValid() {
        return preValid;
    }

    public boolean isMarked() {
        return marked;
    }

    public void setMarked(boolean marked) {
        this.marked = marked;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    public String generateInstanceId() {
        return this.ip + "#" + this.port + "#" + this.cluster.getName() + "#" + this.cluster.getDom().getName();
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof IpAddress)) {
            Loggers.SRV_LOG.error("IPADDRESS-COMPARE", "Object is not an instance of IPAdress,object: " + o.getClass());
            throw new IllegalArgumentException("Object is not an instance of IPAdress,object: " + o.getClass());
        }

        IpAddress ipAddress = (IpAddress) o;
        String ipKey = ipAddress.toString();

        return this.toString().compareTo(ipKey);
    }
}
