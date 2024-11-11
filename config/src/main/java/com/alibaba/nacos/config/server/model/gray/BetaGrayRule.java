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

package com.alibaba.nacos.config.server.model.gray;

import com.alibaba.nacos.api.exception.NacosException;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * beta gray rule for beta ips.
 * @author shiyiyue1102
 */
public class BetaGrayRule extends AbstractGrayRule {
    
    Set<String> betaIps;
    
    public static final String CLIENT_IP_LABEL = "ClientIp";
    
    public static final String TYPE_BETA = "beta";
    
    public static final String VERSION = "1.0.0";
    
    public static final int PRIORITY = Integer.MAX_VALUE;
    
    public BetaGrayRule() {
        super();
    }
    
    public BetaGrayRule(String betaIps, int priority) {
        super(betaIps, priority);
    }
    
    /**
     * parse beta gray rule.
     * @param rawGrayRule raw gray rule.
     * @throws NacosException exception.
     */
    @Override
    protected void parse(String rawGrayRule) throws NacosException {
        Set<String> betaIps = new HashSet<>();
        String[] ips = rawGrayRule.split(",");
        for (String ip : ips) {
            betaIps.add(ip);
        }
        this.betaIps = betaIps;
    }
    
    @Override
    
    public boolean match(Map<String, String> labels) {
        return labels.containsKey(CLIENT_IP_LABEL) && betaIps.contains(labels.get(CLIENT_IP_LABEL));
    }
    
    @Override
    public String getType() {
        return TYPE_BETA;
    }
    
    @Override
    public String getVersion() {
        return VERSION;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BetaGrayRule that = (BetaGrayRule) o;
        return Objects.equals(betaIps, that.betaIps);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(betaIps);
    }
}
