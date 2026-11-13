/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.auth;

import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.common.utils.NamespaceUtil;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Namespace resource parser for Config import and batch delete operations.
 *
 * <p>Both operations bind {@code namespaceId} as a scalar request parameter; absent or blank
 * values mean {@code public}. Validates this field without reading {@code tenant} or applying
 * form {@code !} defaults.</p>
 *
 * @author xiweng.yy
 */
public class ConfigNamespaceHttpResourceParser extends AbstractConfigNamespaceHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request, Secured secured) {
        return NamespaceUtil.processNamespaceParameter(
            readNamespaceParameter(request, "namespaceId", false));
    }
}
