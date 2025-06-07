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

package com.alibaba.nacos.api.naming.pojo.maintainer;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.NacosForm;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.pojo.healthcheck.AbstractHealthChecker;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Cluster detail information for nacos maintain client, contain cluster detail information; instance information is optional.
 *
 * <p>
 *     Cluster detail information can't get by independent, must be get by {@link ServiceDetailInfo#getClusterMap()}
 * </p>
 *
 * @author xiweng.yy
 */
public class ClusterInfo implements NacosForm {

    private static final long serialVersionUID = 2146881454057032105L;

    private String clusterName;
    
    private AbstractHealthChecker healthChecker;
    
    private int healthyCheckPort = 80;
    
    /**
     * Whether Nacos use instance port to do health check.
     */
    private boolean useInstancePortForCheck = true;
    
    private Map<String, String> metadata;
    
    private List<Instance> hosts;
    
    /**
     * Getter method for property <tt>hosts</tt>.
     *
     * @return property value of hosts
     */
    public List<Instance> getHosts() {
        return hosts;
    }
    
    /**
     * Setter method for property <tt>hosts </tt>.
     *
     * @param hosts value to be assigned to property hosts
     */
    public void setHosts(List<Instance> hosts) {
        this.hosts = hosts;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public AbstractHealthChecker getHealthChecker() {
        return healthChecker;
    }
    
    public void setHealthChecker(AbstractHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public int getHealthyCheckPort() {
        return healthyCheckPort;
    }
    
    public void setHealthyCheckPort(int healthyCheckPort) {
        this.healthyCheckPort = healthyCheckPort;
    }
    
    public boolean isUseInstancePortForCheck() {
        return useInstancePortForCheck;
    }
    
    public void setUseInstancePortForCheck(boolean useInstancePortForCheck) {
        this.useInstancePortForCheck = useInstancePortForCheck;
    }
    
    @Override
    public void validate() throws NacosApiException {
        if (StringUtils.isEmpty(clusterName)) {
            this.clusterName = Constants.DEFAULT_CLUSTER_NAME;
        }
    }
}
