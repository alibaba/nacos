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

package com.alibaba.nacos.client.lock.remote.grpc;

import com.alibaba.nacos.api.ability.constant.AbilityKey;
import com.alibaba.nacos.api.ability.constant.AbilityStatus;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.lock.constant.PropertyConstants;
import com.alibaba.nacos.api.lock.model.LockInstance;
import com.alibaba.nacos.api.lock.remote.AbstractLockRequest;
import com.alibaba.nacos.api.lock.remote.LockOperationEnum;
import com.alibaba.nacos.api.lock.remote.request.LockOperationRequest;
import com.alibaba.nacos.api.lock.remote.response.LockOperationResponse;
import com.alibaba.nacos.api.remote.RemoteConstants;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.remote.response.ResponseCode;
import com.alibaba.nacos.client.env.NacosClientProperties;
import com.alibaba.nacos.client.lock.remote.AbstractLockClient;
import com.alibaba.nacos.client.security.SecurityProxy;
import com.alibaba.nacos.client.utils.AppNameUtils;
import com.alibaba.nacos.common.remote.ConnectionType;
import com.alibaba.nacos.common.remote.client.RpcClient;
import com.alibaba.nacos.common.remote.client.RpcClientFactory;
import com.alibaba.nacos.common.remote.client.RpcClientTlsConfigFactory;
import com.alibaba.nacos.common.remote.client.ServerListFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * lock grpc client.
 *
 * @author 985492783@qq.com
 * @description LockGrpcClient
 * @date 2023/6/28 17:35
 */
public class LockGrpcClient extends AbstractLockClient {
    
    private final String uuid;
    
    private final Long requestTimeout;
    
    private final RpcClient rpcClient;
    
    public LockGrpcClient(NacosClientProperties properties, ServerListFactory serverListFactory,
            SecurityProxy securityProxy) throws NacosException {
        super(securityProxy);
        this.uuid = UUID.randomUUID().toString();
        this.requestTimeout = Long.parseLong(properties.getProperty(PropertyConstants.LOCK_REQUEST_TIMEOUT, "-1"));
        Map<String, String> labels = new HashMap<>();
        labels.put(RemoteConstants.LABEL_SOURCE, RemoteConstants.LABEL_SOURCE_SDK);
        labels.put(RemoteConstants.LABEL_MODULE, RemoteConstants.LABEL_MODULE_LOCK);
        labels.put(Constants.APPNAME, AppNameUtils.getAppName());
        this.rpcClient = RpcClientFactory.createClient(uuid, ConnectionType.GRPC, labels,
                RpcClientTlsConfigFactory.getInstance().createSdkConfig(properties.asProperties()));
        start(serverListFactory);
    }
    
    private void start(ServerListFactory serverListFactory) throws NacosException {
        rpcClient.serverListFactory(serverListFactory);
        rpcClient.start();
    }
    
    @Override
    public Boolean lock(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.ACQUIRE);
        LockOperationResponse acquireLockResponse = requestToServer(request, LockOperationResponse.class);
        return (Boolean) acquireLockResponse.getResult();
    }
    
    @Override
    public Boolean unLock(LockInstance instance) throws NacosException {
        if (!isAbilitySupportedByServer()) {
            throw new NacosRuntimeException(NacosException.SERVER_NOT_IMPLEMENTED,
                    "Request Nacos server version is too low, not support lock feature.");
        }
        LockOperationRequest request = new LockOperationRequest();
        request.setLockInstance(instance);
        request.setLockOperationEnum(LockOperationEnum.RELEASE);
        LockOperationResponse acquireLockResponse = requestToServer(request, LockOperationResponse.class);
        return (Boolean) acquireLockResponse.getResult();
    }
    
    @Override
    public void shutdown() throws NacosException {
        rpcClient.shutdown();
    }
    
    private <T extends Response> T requestToServer(AbstractLockRequest request, Class<T> responseClass)
            throws NacosException {
        try {
            request.putAllHeader(getSecurityHeaders());
            Response response =
                    requestTimeout < 0 ? rpcClient.request(request) : rpcClient.request(request, requestTimeout);
            if (ResponseCode.SUCCESS.getCode() != response.getResultCode()) {
                throw new NacosException(response.getErrorCode(), response.getMessage());
            }
            if (responseClass.isAssignableFrom(response.getClass())) {
                return (T) response;
            }
        } catch (NacosException e) {
            throw e;
        } catch (Exception e) {
            throw new NacosException(NacosException.SERVER_ERROR, "Request nacos server failed: ", e);
        }
        throw new NacosException(NacosException.SERVER_ERROR, "Server return invalid response");
    }
    
    private boolean isAbilitySupportedByServer() {
        return rpcClient.getConnectionAbility(AbilityKey.SERVER_DISTRIBUTED_LOCK) == AbilityStatus.SUPPORTED;
    }
}
