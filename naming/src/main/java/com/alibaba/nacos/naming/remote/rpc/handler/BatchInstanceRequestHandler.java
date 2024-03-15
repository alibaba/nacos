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

package com.alibaba.nacos.naming.remote.rpc.handler;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.NamingRemoteConstants;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.naming.remote.response.BatchInstanceResponse;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.core.control.TpsControl;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.paramcheck.impl.BatchInstanceRequestParamExtractor;
import com.alibaba.nacos.core.remote.RequestHandler;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.core.v2.service.impl.EphemeralClientOperationServiceImpl;
import com.alibaba.nacos.naming.utils.InstanceUtil;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import org.springframework.stereotype.Component;

/**
 * The client registers multiple service instance request.
 *
 * @author <a href="mailto:chenhao26@xiaomi.com">chenhao26</a>
 */
@Component("batchInstanceRequestHandler")
public class BatchInstanceRequestHandler extends RequestHandler<BatchInstanceRequest, BatchInstanceResponse> {
    
    private final EphemeralClientOperationServiceImpl clientOperationService;
    
    public BatchInstanceRequestHandler(EphemeralClientOperationServiceImpl clientOperationService) {
        this.clientOperationService = clientOperationService;
    }
    
    @Override
    @TpsControl(pointName = "RemoteNamingInstanceBatchRegister", name = "RemoteNamingInstanceBatchRegister")
    @Secured(action = ActionTypes.WRITE)
    @ExtractorManager.Extractor(rpcExtractor = BatchInstanceRequestParamExtractor.class)
    public BatchInstanceResponse handle(BatchInstanceRequest request, RequestMeta meta) throws NacosException {
        Service service = Service.newService(request.getNamespace(), request.getGroupName(), request.getServiceName(),
                true);
        InstanceUtil.batchSetInstanceIdIfEmpty(request.getInstances(), service.getGroupedServiceName());
        switch (request.getType()) {
            case NamingRemoteConstants.BATCH_REGISTER_INSTANCE:
                return batchRegisterInstance(service, request, meta);
            default:
                throw new NacosException(NacosException.INVALID_PARAM,
                        String.format("Unsupported request type %s", request.getType()));
        }
    }
    
    private BatchInstanceResponse batchRegisterInstance(Service service, BatchInstanceRequest request,
            RequestMeta meta) {
        clientOperationService.batchRegisterInstance(service, request.getInstances(), meta.getConnectionId());
        return new BatchInstanceResponse(NamingRemoteConstants.BATCH_REGISTER_INSTANCE);
    }
}
