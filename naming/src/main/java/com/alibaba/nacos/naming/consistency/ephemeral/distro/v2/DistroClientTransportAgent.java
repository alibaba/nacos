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

package com.alibaba.nacos.naming.consistency.ephemeral.distro.v2;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.core.cluster.Member;
import com.alibaba.nacos.core.cluster.NodeState;
import com.alibaba.nacos.core.cluster.ServerMemberManager;
import com.alibaba.nacos.core.cluster.remote.ClusterRpcClientProxy;
import com.alibaba.nacos.core.distributed.distro.component.DistroCallback;
import com.alibaba.nacos.core.distributed.distro.component.DistroTransportAgent;
import com.alibaba.nacos.core.distributed.distro.entity.DistroData;
import com.alibaba.nacos.core.distributed.distro.entity.DistroKey;
import com.alibaba.nacos.core.distributed.distro.exception.DistroException;
import com.alibaba.nacos.naming.cluster.remote.request.DistroDataRequest;
import com.alibaba.nacos.naming.cluster.remote.response.DistroDataResponse;
import com.alibaba.nacos.naming.misc.Loggers;

/**
 * Distro transport agent for v2.
 *
 * @author xiweng.yy
 */
public class DistroClientTransportAgent implements DistroTransportAgent {
    
    private final ClusterRpcClientProxy clusterRpcClientProxy;
    
    private final ServerMemberManager serverMemberManager;
    
    public DistroClientTransportAgent(ClusterRpcClientProxy clusterRpcClientProxy,
            ServerMemberManager serverMemberManager) {
        this.clusterRpcClientProxy = clusterRpcClientProxy;
        this.serverMemberManager = serverMemberManager;
    }
    
    @Override
    public boolean syncData(DistroData data, String targetServer) {
        DistroDataRequest request = new DistroDataRequest(data, data.getType());
        Member member = serverMemberManager.find(targetServer);
        if (checkTargetServerStatusUnhealthy(member)) {
            Loggers.DISTRO.warn("[DISTRO] Cancel distro sync caused by target server {} unhealthy", targetServer);
            return false;
        }
        try {
            Response response = clusterRpcClientProxy.sendRequest(member, request);
            return checkResponse(response);
        } catch (NacosException e) {
            Loggers.DISTRO.error("[DISTRO-FAILED] Sync distro data failed! ", e);
        }
        return false;
    }
    
    @Override
    public void syncData(DistroData data, String targetServer, DistroCallback callback) {
        // TODO
    }
    
    @Override
    public boolean syncVerifyData(DistroData verifyData, String targetServer) {
        // replace target server as self server so that can callback.
        verifyData.getDistroKey().setTargetServer(serverMemberManager.getSelf().getAddress());
        DistroDataRequest request = new DistroDataRequest(verifyData, DataOperation.VERIFY);
        Member member = serverMemberManager.find(targetServer);
        if (checkTargetServerStatusUnhealthy(member)) {
            Loggers.DISTRO.warn("[DISTRO] Cancel distro verify caused by target server {} unhealthy", targetServer);
            return false;
        }
        try {
            Response response = clusterRpcClientProxy.sendRequest(member, request);
            return checkResponse(response);
        } catch (NacosException e) {
            Loggers.DISTRO.error("[DISTRO-FAILED] Verify distro data failed! ", e);
        }
        return false;
    }
    
    @Override
    public void syncVerifyData(DistroData verifyData, String targetServer, DistroCallback callback) {
        // TODO
    }
    
    @Override
    public DistroData getData(DistroKey key, String targetServer) {
        Member member = serverMemberManager.find(targetServer);
        if (checkTargetServerStatusUnhealthy(member)) {
            throw new DistroException(
                    String.format("[DISTRO] Cancel get snapshot caused by target server %s unhealthy", targetServer));
        }
        DistroDataRequest request = new DistroDataRequest();
        DistroData distroData = new DistroData();
        distroData.setDistroKey(key);
        distroData.setType(DataOperation.QUERY);
        request.setDistroData(distroData);
        request.setDataOperation(DataOperation.QUERY);
        try {
            Response response = clusterRpcClientProxy.sendRequest(member, request);
            if (checkResponse(response)) {
                return ((DistroDataResponse) response).getDistroData();
            } else {
                throw new DistroException(
                        String.format("[DISTRO-FAILED] Get data request to %s failed, code: %d, message: %s",
                                targetServer, response.getErrorCode(), response.getMessage()));
            }
        } catch (NacosException e) {
            throw new DistroException("[DISTRO-FAILED] Get distro data failed! ", e);
        }
    }
    
    @Override
    public DistroData getDatumSnapshot(String targetServer) {
        Member member = serverMemberManager.find(targetServer);
        if (checkTargetServerStatusUnhealthy(member)) {
            throw new DistroException(
                    String.format("[DISTRO] Cancel get snapshot caused by target server %s unhealthy", targetServer));
        }
        DistroDataRequest request = new DistroDataRequest();
        request.setDataOperation(DataOperation.SNAPSHOT);
        try {
            Response response = clusterRpcClientProxy.sendRequest(member, request);
            if (checkResponse(response)) {
                return ((DistroDataResponse) response).getDistroData();
            } else {
                throw new DistroException(
                        String.format("[DISTRO-FAILED] Get snapshot request to %s failed, code: %d, message: %s",
                                targetServer, response.getErrorCode(), response.getMessage()));
            }
        } catch (NacosException e) {
            throw new DistroException("[DISTRO-FAILED] Get distro snapshot failed! ", e);
        }
    }
    
    private boolean checkTargetServerStatusUnhealthy(Member member) {
        return null == member || !NodeState.UP.equals(member.getState());
    }
    
    private boolean checkResponse(Response response) {
        return ResponseCode.SUCCESS.getCode() == response.getResultCode();
    }
}
