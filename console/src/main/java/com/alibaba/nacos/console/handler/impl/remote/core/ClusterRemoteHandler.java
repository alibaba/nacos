/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.console.handler.impl.remote.core;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.console.handler.core.ClusterHandler;
import com.alibaba.nacos.console.handler.impl.remote.EnabledRemoteHandler;
import com.alibaba.nacos.console.handler.impl.remote.NacosMaintainerClientHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * Remote Implementation of ClusterHandler that handles cluster-related operations.
 *
 * @author xiweng.yy
 */
@Service
@EnabledRemoteHandler
public class ClusterRemoteHandler implements ClusterHandler {
    
    private final NacosMaintainerClientHolder clientHolder;
    
    public ClusterRemoteHandler(NacosMaintainerClientHolder clientHolder) {
        this.clientHolder = clientHolder;
    }
    
    /**
     * Retrieves a list of cluster members with an optional search keyword.
     *
     * @param ipKeyWord the search keyword for filtering members
     * @return a collection of matching members
     */
    @Override
    public Collection<? extends NacosMember> getNodeList(String ipKeyWord) throws NacosException {
        return clientHolder.getNamingMaintainerService().listClusterNodes(StringUtils.EMPTY, StringUtils.EMPTY);
    }
}
