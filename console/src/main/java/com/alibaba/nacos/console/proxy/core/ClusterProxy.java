/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.proxy.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Proxy class for handling cluster-related operations.
 *
 * @author zhangyukun
 */
@Service
public class ClusterProxy {
    
    private final ClusterHandler clusterHandler;
    
    /**
     * Constructs a new ClusterProxy with the given ClusterInnerHandler and ConsoleConfig.
     *
     * @param clusterHandler the default implementation of ClusterHandler
     */
    public ClusterProxy(ClusterHandler clusterHandler) {
        this.clusterHandler = clusterHandler;
    }
    
    /**
     * Retrieve a list of cluster members with an optional search keyword.
     *
     * @param ipKeyWord the search keyword for filtering members
     * @return a collection of matching members
     * @throws IllegalArgumentException if the deployment type is invalid
     */
    public Collection<NacosMember> getNodeList(String ipKeyWord) throws NacosException {
        Collection<? extends NacosMember> members = clusterHandler.getNodeList(ipKeyWord);
        List<NacosMember> result = new ArrayList<>();
        members.forEach(member -> {
            if (StringUtils.isBlank(ipKeyWord)) {
                result.add(member);
                return;
            }
            final String address = member.getAddress();
            if (StringUtils.equals(address, ipKeyWord) || StringUtils.startsWith(address, ipKeyWord)) {
                result.add(member);
            }
        });
        result.sort(Comparator.comparing(NacosMember::getAddress));
        return result;
    }
}

