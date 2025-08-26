/*
 *
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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
 *
 */

package com.alibaba.nacos.core.namespace.filter;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.remote.request.Request;
import com.alibaba.nacos.api.remote.request.RequestMeta;
import com.alibaba.nacos.api.remote.response.Response;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.common.paramcheck.ParamInfo;
import com.alibaba.nacos.core.paramcheck.AbstractRpcParamExtractor;
import com.alibaba.nacos.core.paramcheck.ExtractorManager;
import com.alibaba.nacos.core.remote.AbstractRequestFilter;
import com.alibaba.nacos.core.service.NamespaceOperationService;

import com.alibaba.nacos.core.utils.Loggers;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Namespace validation request filter for NamingRequest.
 * not include: DistroDataRequestHandler, HealthCheckRequestHandler, LockRequestHandler,
 * MemberReportHandler, NamingFuzzyWatchRequestHandler: fuzzy watch, ServerLoaderInfoRequestHandler
 *
 * @author FangYuan
 * @since 2025-08-11 21:51:29
 */
@Component
public class NamespaceValidationRequestFilter extends AbstractRequestFilter {

    private final NamespaceOperationService namespaceOperationService;

    public NamespaceValidationRequestFilter(NamespaceOperationService namespaceOperationService) {
        this.namespaceOperationService = namespaceOperationService;
    }

    @Override
    protected Response filter(Request request, RequestMeta meta, Class handlerClazz) throws NacosException {
        try {
            // check global namespace validation config
            boolean namespaceValidationEnabled = NamespaceValidationConfig.getInstance().isNamespaceValidationEnabled();
            if (!namespaceValidationEnabled) {
                return null;
            }
            // extract namespace param
            ExtractorManager.Extractor extractor = getHandleMethod(handlerClazz).getAnnotation(ExtractorManager.Extractor.class);
            if (extractor == null) {
                extractor = (ExtractorManager.Extractor) handlerClazz.getAnnotation(ExtractorManager.Extractor.class);
                if (extractor == null) {
                    return null;
                }
            }
            AbstractRpcParamExtractor paramExtractor = ExtractorManager.getRpcExtractor(extractor);
            List<ParamInfo> paramInfoList = paramExtractor.extractParam(request);

            for (ParamInfo paramInfo : paramInfoList) {
                // if namespace param is null, don't need to check namespace
                String namespaceId = paramInfo.getNamespaceId();
                if (paramInfo.getNamespaceId() == null) {
                    continue;
                }

                boolean exist = isNamespaceExist(namespaceId);
                if (!exist) {
                    Response response = super.getDefaultResponseInstance(handlerClazz);
                    response.setErrorInfo(ErrorCode.NAMESPACE_NOT_EXIST.getCode(),
                            String.format("Namespace '%s' does not exist. Please create the namespace first.", namespaceId));

                    return response;
                }
            }
        } catch (Exception e) {
            Loggers.CORE.warn("Namespace validation error for request: {}, exception: {}", request, e);
        }

        return null;
    }

    private boolean isNamespaceExist(String namespace) {
        boolean namespaceExist;
        try {
            namespaceExist = namespaceOperationService.isNamespaceExist(namespace);
        } catch (NacosApiException e) {
            namespaceExist = true;
        } catch (Exception e) {
            Loggers.CORE.warn("Namespace validation query db error for namespace: {}, exception: {}", namespace, e);
            // throw exception will make the request fail
            namespaceExist = false;
        }

        return namespaceExist;
    }

}