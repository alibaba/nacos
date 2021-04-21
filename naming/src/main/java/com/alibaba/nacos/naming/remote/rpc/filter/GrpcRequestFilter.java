/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.remote.rpc.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.remote.request.AbstractNamingRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.naming.core.v2.upgrade.UpgradeJudgement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Grpc request filter.
 *
 * @author majorhe
 */
@Component
public class GrpcRequestFilter extends AbstractRequestFilter {
    
    @Autowired
    private UpgradeJudgement upgradeJudgement;
    
    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException {
        if (request instanceof AbstractNamingRequest && !upgradeJudgement.isUseGrpcFeatures()) {
            Response response = getDefaultResponseInstance(handlerClazz);
            response.setErrorInfo(NacosException.SERVER_ERROR,
                    "Nacos cluster is running with 1.X mode, can't accept gRPC request temporarily. Please check the server status or close Double write to force open 2.0 mode. Detail https://nacos.io/en-us/docs/2.0.0-upgrading.html.");
            return response;
        }
        return null;
    }
}
