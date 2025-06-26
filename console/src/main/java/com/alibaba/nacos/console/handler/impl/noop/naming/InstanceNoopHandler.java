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

package com.alibaba.nacos.console.handler.impl.noop.naming;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.Page;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.console.handler.naming.InstanceHandler;
import com.alibaba.nacos.naming.model.form.InstanceForm;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Noop Implementation of InstanceHandler that handles instance-related operations.
 * Used when `naming` module is disabled(functionMode is `config`)
 *
 * @author xiweng.yy
 */
@Service
@ConditionalOnMissingBean(value = InstanceHandler.class, ignored = InstanceNoopHandler.class)
public class InstanceNoopHandler implements InstanceHandler {
    
    private static final String MCP_NOT_ENABLED_MESSAGE = "Current functionMode is `config`, naming module is disabled.";
    
    @Override
    public Page<? extends Instance> listInstances(String namespaceId, String serviceNameWithoutGroup, String groupName,
            String clusterName, int page, int pageSize) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
    
    @Override
    public void updateInstance(InstanceForm instanceForm, Instance instance) throws NacosException {
        throw new NacosApiException(NacosException.SERVER_NOT_IMPLEMENTED, ErrorCode.API_FUNCTION_DISABLED,
                MCP_NOT_ENABLED_MESSAGE);
    }
}

