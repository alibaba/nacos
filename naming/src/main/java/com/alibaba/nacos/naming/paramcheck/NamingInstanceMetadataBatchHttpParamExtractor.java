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

package com.alibaba.nacos.naming.paramcheck;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.paramcheck.AbstractHttpParamExtractor;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.fasterxml.jackson.core.type.TypeReference;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * Naming instance metadata batch http param extractor.
 *
 * @author zhuoguang
 */
public class NamingInstanceMetadataBatchHttpParamExtractor extends AbstractHttpParamExtractor {
    
    @Override
    public List<ParamInfo> extractParam(HttpServletRequest request) throws NacosException {
        ParamInfo paramInfo = new ParamInfo();
        String serviceName = request.getParameter("serviceName");
        String groupName = request.getParameter("groupName");
        String groupServiceName = serviceName;
        if (StringUtils.isNotBlank(groupServiceName) && groupServiceName.contains(Constants.SERVICE_INFO_SPLITER)) {
            String[] splits = groupServiceName.split(Constants.SERVICE_INFO_SPLITER, 2);
            groupName = splits[0];
            serviceName = splits[1];
        }
        paramInfo.setServiceName(serviceName);
        paramInfo.setGroup(groupName);
        paramInfo.setNamespaceId(request.getParameter("namespaceId"));
        paramInfo.setMetadata(UtilsAndCommons.parseMetadata(request.getParameter("metadata")));
        ArrayList<ParamInfo> paramInfos = new ArrayList<>();
        paramInfos.add(paramInfo);
        
        String instances = request.getParameter("instances");
        if (StringUtils.isNotBlank(instances)) {
            List<Instance> targetInstances = JacksonUtils.toObj(instances, new TypeReference<List<Instance>>() {
            });
            for (Instance instance : targetInstances) {
                ParamInfo instanceParamInfo = new ParamInfo();
                instanceParamInfo.setIp(instance.getIp());
                instanceParamInfo.setPort(String.valueOf(instance.getPort()));
                instanceParamInfo.setCluster(instance.getClusterName());
                paramInfos.add(instanceParamInfo);
            }
        }
        return paramInfos;
    }
}
