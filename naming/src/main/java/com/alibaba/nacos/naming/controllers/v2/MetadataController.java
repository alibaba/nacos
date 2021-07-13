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

package com.alibaba.nacos.naming.controllers.v2;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.CommonParams;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.auth.common.ActionTypes;
import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.common.model.RestResultUtils;
import com.alibaba.nacos.core.utils.WebUtils;
import com.alibaba.nacos.naming.core.v2.ServiceManager;
import com.alibaba.nacos.naming.core.v2.metadata.InstanceMetadata;
import com.alibaba.nacos.naming.core.v2.metadata.NamingMetadataOperateService;
import com.alibaba.nacos.naming.core.v2.pojo.InstancePublishInfo;
import com.alibaba.nacos.naming.core.v2.pojo.Service;
import com.alibaba.nacos.naming.misc.UtilsAndCommons;
import com.alibaba.nacos.naming.pojo.instance.HttpRequestInstanceBuilder;
import com.alibaba.nacos.naming.web.NamingResourceParser;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * The metadata openAPI controller for nacos v2.x.
 *
 * @author xiweng.yy
 */
@RestController
@RequestMapping(UtilsAndCommons.NACOS_NAMING_CONTEXT_2 + UtilsAndCommons.NACOS_NAMING_METADATA_CONTEXT)
public class MetadataController {
    
    private static final String SERVICE_NOT_FOUNT_MSG = "Group %s, Service %s not found in namespaceId %s";
    
    private final NamingMetadataOperateService metadataOperateService;
    
    public MetadataController(NamingMetadataOperateService metadataOperateService) {
        this.metadataOperateService = metadataOperateService;
    }
    
    /**
     * Update instance metadata.
     *
     * <p>
     * This API will update the metadata of instance, which will use raft to keep the consistency. And this API is the
     * higher priority than instance update API for metadata.
     * </p>
     *
     * <p>
     * This API will not full replace all metadata which instance published, only replace those instance published
     * metadata items which has same keys and all of metadata items updated by this API last time.
     * </p>
     *
     * <p>
     * For example, The instance register with publish metadata `a=b&c=d`, and update metadata with `e=f` by this API.
     * The metadata will become `a=b&c=d&e=f`; And then update metadata with `c=e&g=h` by this API again, the result
     * metadata be `a=b&c=e&g=h`
     * </p>
     *
     * @param request update request.
     * @return update result.
     * @throws NacosException any nacos exception during update.
     */
    @PutMapping
    @Secured(parser = NamingResourceParser.class, action = ActionTypes.WRITE)
    public RestResult<String> updateInstanceMetadata(HttpServletRequest request) throws NacosException {
        String namespaceId = WebUtils.optional(request, CommonParams.NAMESPACE_ID, Constants.DEFAULT_NAMESPACE_ID);
        String group = WebUtils.optional(request, CommonParams.GROUP_NAME, Constants.DEFAULT_GROUP);
        String serviceName = WebUtils.required(request, CommonParams.SERVICE_NAME);
        Optional<Service> service = ServiceManager.getInstance().getSingletonIfExist(namespaceId, group, serviceName);
        if (!service.isPresent()) {
            return RestResultUtils.failedWithMsg(NacosException.NOT_FOUND,
                    String.format(SERVICE_NOT_FOUNT_MSG, group, serviceName, namespaceId));
        }
        Instance instance = HttpRequestInstanceBuilder.newBuilder().setRequest(request).build();
        String metadataId = InstancePublishInfo
                .genMetadataId(instance.getIp(), instance.getPort(), instance.getClusterName());
        metadataOperateService.updateInstanceMetadata(service.get(), metadataId, buildMetadata(instance));
        return RestResultUtils.success("Update metadata completed", null);
    }
    
    private InstanceMetadata buildMetadata(Instance instance) {
        InstanceMetadata result = new InstanceMetadata();
        result.setEnabled(instance.isEnabled());
        result.setWeight(instance.getWeight());
        result.getExtendData().putAll(instance.getMetadata());
        return result;
    }
}
