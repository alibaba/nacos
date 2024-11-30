/*
 * Copyright 2024-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.utils;

import com.alibaba.nacos.common.paramcheck.ParamCheckResponse;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.namespace.repository.NamespacePersistService;
import com.alibaba.nacos.sys.utils.ApplicationUtils;

/**
 * <p>
 * namespace checker util.
 * </p>
 *
 * @author fuhouyu
 * @since 2024/11/30 17:47
 */
public class NamespaceParamCheckUtils {
    
    private NamespaceParamCheckUtils() {
    
    }
    
    /**
     * check namespaceId exists.
     * if namespace is null or empty. return true.
     * else query namespace by id. when not exists,then return false.
     * @param namespaceId namespaceId
     * @return paramCheckResponse
     */
    public static ParamCheckResponse checkNamespaceExists(String namespaceId) {
        ParamCheckResponse paramCheckResponse = new ParamCheckResponse();
        if (StringUtils.isEmpty(namespaceId)) {
            paramCheckResponse.setSuccess(true);
            return paramCheckResponse;
        }
        NamespacePersistService namespacePersistService = ApplicationUtils.getBean(NamespacePersistService.class);
        int count = namespacePersistService.tenantInfoCountByTenantId(namespaceId);
        // if namespaceId is not exists, return false.
        if (count == 0) {
            paramCheckResponse.setSuccess(false);
            paramCheckResponse.setMessage("namespaceId [ " + namespaceId + " ] not exist");
            return paramCheckResponse;
        }
        // else return true
        paramCheckResponse.setSuccess(true);
        return paramCheckResponse;
    }
}
