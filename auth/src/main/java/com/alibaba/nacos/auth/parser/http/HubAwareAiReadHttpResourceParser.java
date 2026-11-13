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

package com.alibaba.nacos.auth.parser.http;

import com.alibaba.nacos.api.ai.constant.AiConstants;
import com.alibaba.nacos.auth.annotation.Secured;
import com.alibaba.nacos.plugin.auth.api.Resource;
import com.alibaba.nacos.plugin.auth.constant.ActionTypes;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.auth.constant.SignType;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Properties;

/**
 * Select the existing Hub read admission resource inside an existing AI GET operation. Ordinary
 * namespace requests retain their original AI resource parsing and authorization properties.
 * This parser must be selected explicitly on read endpoints, never on mutations.
 */
public class HubAwareAiReadHttpResourceParser extends AiHttpResourceParser {
    
    @Override
    public Resource parse(HttpServletRequest request, Secured secured) {
        return selectsHub(request, secured) ? hubReadResource(secured)
            : super.parse(request, secured);
    }
    
    static boolean selectsHub(HttpServletRequest request, Secured secured) {
        String namespace =
            request.getParameter(com.alibaba.nacos.api.common.Constants.NAMESPACE_ID);
        String[] namespaces = request.getParameterValues(
            com.alibaba.nacos.api.common.Constants.NAMESPACE_ID);
        boolean hub = AiConstants.Hub.NAMESPACE_ID.equals(namespace);
        if (namespaces != null) {
            for (String value : namespaces) {
                hub |= AiConstants.Hub.NAMESPACE_ID.equals(value);
            }
        }
        if (!hub) {
            return false;
        }
        if ((namespaces != null && (namespaces.length != 1
            || !AiConstants.Hub.NAMESPACE_ID.equals(namespaces[0])))
            || !AiConstants.Hub.NAMESPACE_ID.equals(namespace)) {
            throw new IllegalArgumentException("Conflicting namespaceId parameters for Hub read");
        }
        if (!"GET".equals(request.getMethod()) || secured.action() != ActionTypes.READ) {
            throw new IllegalArgumentException("Reserved Hub namespace is read-only");
        }
        return true;
    }
    
    static Resource hubReadResource(Secured secured) {
        Properties properties = new Properties();
        for (String tag : secured.tags()) {
            properties.setProperty(tag, tag);
        }
        properties.setProperty(Constants.Resource.ACTION, ActionTypes.READ.toString());
        properties.setProperty(Constants.Tag.ONLY_IDENTITY, Constants.Tag.ONLY_IDENTITY);
        properties.setProperty(Constants.Tag.ALLOW_ANONYMOUS, Constants.Tag.ALLOW_ANONYMOUS);
        return new Resource("", "", AiConstants.Hub.ENTRY_RESOURCE, SignType.SPECIFIED, properties);
    }
}
