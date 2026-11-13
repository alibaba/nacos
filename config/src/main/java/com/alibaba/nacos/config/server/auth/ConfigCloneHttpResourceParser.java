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
import com.alibaba.nacos.plugin.auth.constant.Constants;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Arrays;

/**
 * Target WRITE resource parser for v3 Admin and Console clone operations.
 *
 * <p>Admin uses {@code namespaceId} as the target and {@code sourceNamespaceId} as the source.
 * Console retains {@code SECURED_SPECIAL_TAGS}, using {@code targetNamespaceId} as the target
 * and {@code namespaceId} as the source. An explicitly empty target means {@code public} and
 * never falls back to the source. Source and target may differ; the business layer retains its
 * same-namespace default for an absent or blank source and checks source READ permission
 * independently. Resource parsing does not read or compare {@code tenant}.</p>
 *
 * @author xiweng.yy
 */
public class ConfigCloneHttpResourceParser extends AbstractConfigNamespaceHttpResourceParser {
    
    @Override
    protected String getNamespaceId(HttpServletRequest request, Secured secured) {
        boolean console =
            Arrays.asList(secured.tags()).contains(Constants.Tag.SECURED_SPECIAL_TAGS);
        String targetParameter = console ? "targetNamespaceId" : "namespaceId";
        String sourceParameter = console ? "namespaceId" : "sourceNamespaceId";
        String target = readNamespaceParameter(request, targetParameter, false);
        readNamespaceParameter(request, sourceParameter, false);
        return NamespaceUtil.processNamespaceParameter(target);
    }
}
