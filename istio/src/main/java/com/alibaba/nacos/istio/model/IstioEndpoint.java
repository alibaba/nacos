/*
 *
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.istio.model;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.google.protobuf.UInt32Value;
import io.envoyproxy.envoy.config.core.v3.Address;
import io.envoyproxy.envoy.config.core.v3.Locality;
import io.envoyproxy.envoy.config.core.v3.SocketAddress;
import io.envoyproxy.envoy.config.endpoint.v3.Endpoint;
import io.envoyproxy.envoy.config.endpoint.v3.LbEndpoint;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

import static com.alibaba.nacos.istio.util.IstioCrdUtil.ISTIO_HOSTNAME;

/**.
 * @author RocketEngine26
 * @date 2022/8/9 10:29
 */
public class IstioEndpoint {
    private LbEndpoint lbEndpoint;
    
    private Instance instance;
    
    private Locality locality;
    
    private String protocol;
    
    private String hostName;
    
    private String clusterName;
    
    public IstioEndpoint(Instance instance) {
        this.instance = instance;
        this.hostName = StringUtils.isNotEmpty(instance.getMetadata().get(ISTIO_HOSTNAME)) ? instance.getMetadata().get(ISTIO_HOSTNAME) : "";
        this.clusterName = StringUtils.isNotEmpty(instance.getClusterName()) ? instance.getClusterName() : "";
        
        if (StringUtils.isNotEmpty(instance.getMetadata().get("protocol"))) {
            this.protocol = instance.getMetadata().get("protocol");
        
            if ("triple".equals(this.protocol) || "tri".equals(this.protocol)) {
                this.protocol = "grpc";
            }
        } else {
            this.protocol = "http";
        }
        
        buildLocality();
    }
    
    private void buildLocality() {
        String region = instance.getMetadata().getOrDefault("region", "");
        String zone = instance.getMetadata().getOrDefault("zone", "");
        String subzone = instance.getMetadata().getOrDefault("subzone", "");
        
        this.locality = Locality.newBuilder().setRegion(region).setZone(zone).setSubZone(subzone).build();
    }
    
    private LbEndpoint buildLbEndpoint() {
        Address adder = Address.newBuilder().setSocketAddress(SocketAddress.newBuilder().setAddress(instance.getIp())
                .setPortValue(this.instance.getPort()).setProtocol(SocketAddress.Protocol.TCP).build()).build();
        this.lbEndpoint = LbEndpoint.newBuilder().setLoadBalancingWeight(UInt32Value.newBuilder().setValue(
                (int) this.instance.getWeight())).setEndpoint(Endpoint.newBuilder().setAddress(adder).build()).build();
        
        return this.lbEndpoint;
    }
    
    public Map<String, String> getLabels() {
        return instance.getMetadata();
    }
    
    public String getAdder() {
        return instance.getIp();
    }
    
    public LbEndpoint getLbEndpoint() {
        return buildLbEndpoint();
    }
    
    public String getStringLocality() {
        return locality.getRegion() + "." +  locality.getZone() + "." + locality.getSubZone();
    }
    
    public Locality getLocality() {
        return locality;
    }
    
    public int getPort() {
        return instance.getPort();
    }
    
    public String getProtocol() {
        return protocol;
    }
    
    public int getWeight() {
        return (int) instance.getWeight();
    }
    
    public String getHostName() {
        return hostName;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public boolean isHealthy() {
        return instance.isHealthy();
    }
    
    public boolean isEnabled() {
        return instance.isEnabled();
    }
}