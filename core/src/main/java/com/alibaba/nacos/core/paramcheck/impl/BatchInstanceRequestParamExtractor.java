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

package com.alibaba.nacos.core.paramcheck.impl;

import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.naming.remote.request.BatchInstanceRequest;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Param Extractor and check for grpc batch instance request{@link BatchInstanceRequest}.
 *
 * @author zhuoguang
 */
public class BatchInstanceRequestParamExtractor extends AbstractRpcParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(Request request) {
        BatchInstanceRequest req = (BatchInstanceRequest) request;
        ParamInfo paramInfo = new ParamInfo();
        paramInfo.setNamespaceId(req.getNamespace());
        paramInfo.setServiceName(req.getServiceName());
        paramInfo.setGroup(req.getGroupName());
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        List<Instance> instanceList = req.getInstances();
        if (instanceList == null) {
            return paramInfos;
        }
        for (Instance instance : instanceList) {
            ParamInfo instanceParamInfo = new ParamInfo();
            instanceParamInfo.setIp(instance.getIp());
            instanceParamInfo.setPort(String.valueOf(instance.getPort()));
            instanceParamInfo.setServiceName(instance.getServiceName());
            instanceParamInfo.setCluster(instance.getClusterName());
            instanceParamInfo.setMetadata(instance.getMetadata());
            paramInfos.add(instanceParamInfo);
        }
        return paramInfos;
    }
}
