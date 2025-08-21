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

package com.alibaba.nacos.core.namespace.model.form;

import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.utils.StringUtils;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Extend namespace form to adapt create nacos namespace API.
 *
 * @author xiweng.yy
 */
public class CreateNamespaceForm extends NamespaceForm {
    
    private static final long serialVersionUID = 1069121416033814056L;
    
    private String customNamespaceId;
    
    public String getCustomNamespaceId() {
        return customNamespaceId;
    }
    
    public void setCustomNamespaceId(String customNamespaceId) {
        this.customNamespaceId = customNamespaceId;
    }
    
    @Override
    public void validate() throws NacosApiException {
        if (null == super.getNamespaceName()) {
            throw new NacosApiException(HttpStatus.BAD_REQUEST.value(), ErrorCode.PARAMETER_MISSING,
                    "required parameter 'namespaceName' is missing");
        }
        if (StringUtils.isBlank(customNamespaceId)) {
            customNamespaceId = UUID.randomUUID().toString();
        } else {
            customNamespaceId = customNamespaceId.trim();
        }
    }
}
