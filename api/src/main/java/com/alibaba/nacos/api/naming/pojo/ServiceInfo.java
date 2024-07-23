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

import com.alibaba.nacos.api.common.Constants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * ServiceInfo.
 *
 * @author nkorange
 * @author shizhengxing
 */
@JsonInclude(Include.NON_NULL)
public class ServiceInfo {
    
    /**
     * file name pattern: groupName@@name@clusters.
     */
    private static final int GROUP_POSITION = 0;
    
    private static final int SERVICE_POSITION = 1;
    
    private static final int CLUSTER_POSITION = 2;
    
    private static final int FILE_NAME_PARTS = 3;
    
    @JsonIgnore
    private String jsonFromServer = EMPTY;
    
    private static final String EMPTY = "";
    
    private static final String DEFAULT_CHARSET = "UTF-8";
    
    private String name;
    
    private String groupName;
    
    private String clusters;
    
    private long cacheMillis = 1000L;
    
    private List<Instance> hosts = new ArrayList<>();
    
    private long lastRefTime = 0L;
    
    private String checksum = "";
    
    private volatile boolean allIPs = false;
    
    private volatile boolean reachProtectionThreshold = false;
    
    public ServiceInfo() {
    }
    
    public boolean isAllIPs() {
        return allIPs;
    }
    
    public void setAllIPs(boolean allIPs) {
        this.allIPs = allIPs;
    }
    
    /**
     * There is only one form of the key:groupName@@name@clusters. This constructor used by DiskCache.read(String) and
     * FailoverReactor.FailoverFileReader,you should know that 'groupName' must not be null,and 'clusters' can be null.
     */
    public ServiceInfo(final String key) {
        String[] keys = key.split(Constants.SERVICE_INFO_SPLITER);
        if (keys.length >= FILE_NAME_PARTS) {
            this.groupName = keys[GROUP_POSITION];
            this.name = keys[SERVICE_POSITION];
            this.clusters = keys[CLUSTER_POSITION];
        } else if (keys.length == CLUSTER_POSITION) {
            this.groupName = keys[GROUP_POSITION];
            this.name = keys[SERVICE_POSITION];
        } else {
            //defensive programming
            throw new IllegalArgumentException("Can't parse out 'groupName',but it must not be null!");
        }
    }
    
    public ServiceInfo(String name, String clusters) {
        this.name = name;
        this.clusters = clusters;
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
    
    public void addHost(Instance host) {
        hosts.add(host);
    }
    
    public void addAllHosts(List<? extends Instance> hosts) {
        this.hosts.addAll(hosts);
    }
    
    public List<Instance> getHosts() {
        return new ArrayList<>(hosts);
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
    
    public String getGroupName() {
        return groupName;
    }
    
    public void setGroupName(String groupName) {
        this.groupName = groupName;
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
    
    /**
     * Judge whether service info is validate.
     *
     * @return true if validate, otherwise false
     */
    public boolean validate() {
        if (isAllIPs()) {
            return true;
        }
        
        if (hosts == null) {
            return false;
        }
        
        boolean existValidHosts = false;
        for (Instance host : hosts) {
            if (host.isHealthy() && host.getWeight() > 0) {
                existValidHosts = true;
                break;
            }
        }
        return existValidHosts;
    }
    
    @JsonIgnore
    public String getJsonFromServer() {
        return jsonFromServer;
    }
    
    public void setJsonFromServer(String jsonFromServer) {
        this.jsonFromServer = jsonFromServer;
    }
    
    @JsonIgnore
    public String getKey() {
        String serviceName = getGroupedServiceName();
        return getKey(serviceName, clusters);
    }
    
    @JsonIgnore
    public static String getKey(String name, String clusters) {
        
        if (!isEmpty(clusters)) {
            return name + Constants.SERVICE_INFO_SPLITER + clusters;
        }
        
        return name;
    }
    
    @JsonIgnore
    public String getKeyEncoded() {
        String serviceName = getGroupedServiceName();
        try {
            serviceName = URLEncoder.encode(serviceName, DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException ignored) {
        }
        return getKey(serviceName, clusters);
    }
    
    private String getGroupedServiceName() {
        String serviceName = this.name;
        if (!isEmpty(groupName) && !serviceName.contains(Constants.SERVICE_INFO_SPLITER)) {
            serviceName = groupName + Constants.SERVICE_INFO_SPLITER + serviceName;
        }
        return serviceName;
    }
    
    /**
     * Get {@link ServiceInfo} from key.
     *
     * @param key key of service info
     * @return new service info
     */
    public static ServiceInfo fromKey(final String key) {
        return new ServiceInfo(key);
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
    
    public boolean isReachProtectionThreshold() {
        return reachProtectionThreshold;
    }
    
    public void setReachProtectionThreshold(boolean reachProtectionThreshold) {
        this.reachProtectionThreshold = reachProtectionThreshold;
    }
}
