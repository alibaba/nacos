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
import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.naming.healthcheck.HealthCheckStatus;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import org.springframework.util.Assert;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * IP under service
 *
 * @author nkorange
 * @author jifengnan 2019-5-28
 */
public class Instance extends com.alibaba.nacos.api.naming.pojo.Instance implements Comparable {

    private static final double MAX_WEIGHT_VALUE = 10000.0D;
    private static final double MIN_POSITIVE_WEIGHT_VALUE = 0.01D;
    private static final double MIN_WEIGHT_VALUE = 0.00D;

    private volatile long lastBeat = System.currentTimeMillis();

    @JSONField(serialize = false)
    private volatile boolean mockValid = false;

    private volatile boolean marked = false;

    private String tenant;

    private String app;

    /**
     * The cluster to which the current instance belongs
     */
    private Cluster cluster;

    private static final Pattern IP_PATTERN
        = Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):?(\\d{1,5})?");

    private static final Pattern ONLY_DIGIT_AND_DOT
        = Pattern.compile("(\\d|\\.)+");

    private static final String SPLITER = "_";

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

    /**
     * Create a new instance.
     *
     * @param ip      the IP of the current instance. The IP format can be ddd.ddd.ddd.ddd or a domain name(nacos.io).
     * @param port    the port of the current instance
     * @param cluster the cluster to which the current instance belongs
     * @throws IllegalArgumentException if the IP or port format is invalid, or the cluster is null.
     */
    public Instance(String ip, int port, Cluster cluster) {
        super.setIp(ip);
        super.setPort(port);
        Assert.notNull(cluster, "Cluster cannot be null");
        this.cluster = cluster;
        super.setClusterName(cluster.getName());
        super.setServiceName(cluster.getService().getName());
        validate();
    }

    /**
     * Create a new instance.
     * <p>
     * For fast json deserialization only, which means it can be removed if a new deserialization way be added.
     *
     * @param ip          the IP of the current instance. The IP format can be ddd.ddd.ddd.ddd or a domain name(nacos.io).
     * @param port        the port of the current instance
     * @param cluster     the cluster to which the current instance belongs
     * @param clusterName the cluster name
     * @param serviceName the service name
     * @throws IllegalArgumentException if the IP or port format is invalid, or the cluster is null.
     */
    @JSONCreator
    private Instance(String ip, int port, Cluster cluster, String clusterName, String serviceName) {
        super.setIp(ip);
        super.setPort(port);
        this.cluster = cluster;
        super.setClusterName(clusterName);
        super.setServiceName(serviceName);
        validate();
    }

    /**
     * Get the cluster name to which the current instance belongs.
     *
     * <p>Note that the returned cluster name may not be the name which set by {@link #setClusterName(String)},
     * but the name of the cluster to which the current instance belongs({@link Cluster#getName()}).
     *
     * @return the cluster name
     */
    @Override
    public String getClusterName() {
        if (cluster != null) {
            return cluster.getName();
        }
        return super.getClusterName();
    }

    /**
     * Replace the cluster name for the current instance.
     * <p>
     * Deprecated because the cluster name shouldn't be replaced, the correct way is to create a new instance.
     * This method is just for backward compatibility.
     *
     * @param clusterName the new cluster name
     */
    @Override
    @Deprecated
    public void setClusterName(String clusterName) {
        super.setClusterName(clusterName);
    }

    /**
     * Replace the service name for the current instance.
     * <p>
     * Deprecated because the service name shouldn't be replaced, the correct way is to create a new instance.
     * This method is just for backward compatibility.
     *
     * @param serviceName the new service name
     */
    @Override
    @Deprecated
    public void setServiceName(String serviceName) {
        super.setServiceName(serviceName);
    }

    /**
     * Get the service name.
     *
     *
     * <p>Note that the returned service name may not be the name which set by {@link #setServiceName(String)},
     * but the name of the service to which the current instance belongs({@link Service#getName()}).
     *
     * @return the service name
     */
    @Override
    public String getServiceName() {
        if (cluster != null) {
            return cluster.getService().getName();
        }
        return super.getServiceName();
    }


    /**
     * Replace the IP of the current instance.
     * <p>
     * Deprecated because the IP shouldn't be replaced, the correct way is to create a new instance.
     * This method is just for backward compatibility.
     *
     * @param ip the new IP
     */
    @Override
    @Deprecated
    public void setIp(String ip) {
        super.setIp(ip);
    }

    /**
     * Replace the port of the current instance.
     * <p>
     * Deprecated because the port shouldn't be replaced, the correct way is to create a new instance.
     * This method is just for backward compatibility.
     *
     * @param port the new port
     */
    @Override
    @Deprecated
    public void setPort(int port) {
        super.setPort(port);
    }

    public String toIPAddr() {
        return getIp() + ":" + getPort();
    }

    @Override
    public String toString() {
        return getDatumKey() + SPLITER + getWeight() + SPLITER + isHealthy() + SPLITER + marked + SPLITER + getClusterName();
    }

    public String toJSON() {
        return JSON.toJSONString(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (null == obj || obj.getClass() != getClass()) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        Instance other = (Instance) obj;

        // 0 means wild
        return getIp().equals(other.getIp()) && (getPort() == other.getPort() || getPort() == 0)
            && this.isEphemeral() == other.isEphemeral();
    }

    @JSONField(serialize = false)
    public String getDatumKey() {
        if (getPort() > 0) {
            return getIp() + ":" + getPort() + ":" + UtilsAndCommons.LOCALHOST_SITE + ":" + getClusterName();
        } else {
            return getIp() + ":" + UtilsAndCommons.LOCALHOST_SITE + ":" + getClusterName();
        }
    }

    @JSONField(serialize = false)
    public String getDefaultKey() {
        if (getPort() > 0) {
            return getIp() + ":" + getPort() + ":" + UtilsAndCommons.UNKNOWN_SITE;
        } else {
            return getIp() + ":" + UtilsAndCommons.UNKNOWN_SITE;
        }
    }

    @Override
    public int hashCode() {
        return getIp().hashCode();
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

    public Cluster getCluster() {
        return cluster;
    }

    public String generateInstanceId() {
        return getIp() + "#" + getPort() + "#" + getClusterName() + "#" + getServiceName();
    }


    public String generateInstanceId(Set<String> currentInstanceIds) {
        String instanceIdGenerator = getInstanceIdGenerator();
        if (Constants.SNOWFLAKE_INSTANCE_ID_GENERATOR.equalsIgnoreCase(instanceIdGenerator)) {
            return generateSnowflakeInstanceId(currentInstanceIds);
        } else {
            return generateInstanceId();
        }
    }

    /**
     * Generate instance id which could be used for snowflake algorithm.
     * @param currentInstanceIds existing instance ids, which can not be used by new instance.
     * @return
     */
    private String generateSnowflakeInstanceId(Set<String> currentInstanceIds) {
        int id = 0;
        while (currentInstanceIds.contains(String.valueOf(id))) {
            id++;
        }
        currentInstanceIds.add(String.valueOf(id));
        return String.valueOf(id);
    }

    public void validate() throws NacosException {
        if (onlyContainsDigitAndDot()) {
            Matcher matcher = IP_PATTERN.matcher(getIp() + ":" + getPort());
            if (!matcher.matches()) {
                throw new IllegalArgumentException(String.format("IP(%s) or port(%d) format is invalid", getIp(), getPort()));

            }
        }

        if (getWeight() > MAX_WEIGHT_VALUE || getWeight() < MIN_WEIGHT_VALUE) {
            throw new IllegalArgumentException(
                String.format("Illegal weight value: %f, the legal value range should be %.1f - %.1f", getWeight(),
                    MIN_WEIGHT_VALUE, MAX_WEIGHT_VALUE));
        }
    }

    /**
     * Whether the IP consists of digits and dots.
     * Its to support host name.
     *
     * @return true if the IP consists of digits and dots only.
     */
    private boolean onlyContainsDigitAndDot() {
        Assert.notNull(getIp(), "IP cannot be null");
        Matcher matcher = ONLY_DIGIT_AND_DOT.matcher(getIp());
        return matcher.matches();
    }

    @Override
    public int compareTo(Object o) {
        if (!(o instanceof Instance)) {
            Loggers.SRV_LOG.error("[INSTANCE-COMPARE] Object is not an instance of IPAdress, object: {}", o.getClass());
            throw new IllegalArgumentException("Object is not an instance of IPAdress,object: " + o.getClass());
        }

        Instance instance = (Instance) o;
        String ipKey = instance.toString();

        return this.toString().compareTo(ipKey);
    }
}
