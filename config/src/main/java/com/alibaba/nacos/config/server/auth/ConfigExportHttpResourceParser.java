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
 * Namespace resource parser for Config export operations.
 *
 * <p>{@code ConfigFormV3} selects the export scope through {@code namespaceId}; absent or blank
 * values mean {@code public}. Validates form {@code []} and {@code !} fallback fields and
 * multipart file fields, giving precedence to an explicit primary field. Does not read
 * {@code tenant}.</p>
 *
 * @author xiweng.yy
 */
public class ConfigExportHttpResourceParser extends AbstractConfigNamespaceHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request, Secured secured) {
        return NamespaceUtil.processNamespaceParameter(
            readNamespaceParameter(request, "namespaceId", true));
    }
}
