/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service;

import com.alibaba.nacos.ai.model.AiResource;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.core.context.RequestContextHolder;
import com.alibaba.nacos.plugin.auth.api.IdentityContext;
import com.alibaba.nacos.plugin.auth.constant.Constants;
import com.alibaba.nacos.plugin.datafilter.constant.DataFilterConstants;
import com.alibaba.nacos.plugin.datafilter.model.FilterableResource;
import com.alibaba.nacos.plugin.datafilter.spi.DataFilterPluginManager;
import com.alibaba.nacos.plugin.datafilter.spi.DataFilterService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Helper for data-level filtering in AI service layer.
 *
 * @author nacos
 */
public class DataFilterHelper {

    private static final String DATA_FILTER_SERVICE_NAME = "nacos-default-ai";

    private DataFilterHelper() {
    }

    /**
     * Resolve the current user from request context using the plugin-level identity abstraction.
     */
    public static String resolveCurrentUser() {
        try {
            IdentityContext identity = RequestContextHolder.getContext().getAuthContext().getIdentityContext();
            Object id = identity.getParameter(Constants.Identity.IDENTITY_ID);
            return id == null ? "" : id.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Filter candidate resources by read permission for current user.
     *
     * @param candidates candidate resources
     * @param <T>        filterable resource type
     * @return resources the current user is allowed to read
     */
    public static <T extends FilterableResource> List<T> doReadFilter(List<T> candidates) {
        Optional<DataFilterService> filterService = DataFilterPluginManager.getInstance()
                .findFilterService(DATA_FILTER_SERVICE_NAME);
        if (!filterService.isPresent()) {
            return candidates;
        }
        return filterService.get().filter(resolveCurrentUser(), DataFilterConstants.ACTION_READ, null, candidates);
    }

    /**
     * Check write permission for current user on the given resource. Throws 403 if denied.
     *
     * @param resource the resource to check
     * @throws NacosException if no write permission
     */
    public static void doWriteCheck(AiResource resource) throws NacosException {
        Optional<DataFilterService> filterService = DataFilterPluginManager.getInstance()
                .findFilterService(DATA_FILTER_SERVICE_NAME);
        if (!filterService.isPresent()) {
            return;
        }
        List<AiResource> result = filterService.get()
                .filter(resolveCurrentUser(), DataFilterConstants.ACTION_WRITE, null, Collections.singletonList(resource));
        if (result.isEmpty()) {
            throw new NacosApiException(NacosException.NO_RIGHT, ErrorCode.ACCESS_DENIED,
                    "No permission to modify skill: " + resource.getName());
        }
    }
}
