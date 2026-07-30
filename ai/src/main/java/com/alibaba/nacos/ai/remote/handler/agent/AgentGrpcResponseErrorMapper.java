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

package com.alibaba.nacos.ai.remote.handler.agent;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.exception.api.NacosApiException;
import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;
import com.alibaba.nacos.api.model.v2.ErrorCode;
import com.alibaba.nacos.api.remote.response.Response;

/**
 * Maps Agent Client application failures to gRPC response error categories.
 *
 * @author Nacos
 */
final class AgentGrpcResponseErrorMapper {
    
    private AgentGrpcResponseErrorMapper() {
    }
    
    static void apply(Response response, Exception exception) {
        if (exception instanceof NacosApiException) {
            NacosApiException apiException = (NacosApiException) exception;
            response.setErrorInfo(apiException.getDetailErrCode(), apiException.getErrMsg());
        } else if (exception instanceof IllegalArgumentException) {
            response.setErrorInfo(ErrorCode.PARAMETER_VALIDATE_ERROR.getCode(),
                exception.getMessage());
        } else if (exception instanceof NacosRuntimeException) {
            NacosRuntimeException runtimeException = (NacosRuntimeException) exception;
            response.setErrorInfo(runtimeException.getErrCode(), runtimeException.getMessage());
        } else if (exception instanceof NacosException) {
            NacosException nacosException = (NacosException) exception;
            response.setErrorInfo(nacosException.getErrCode(), nacosException.getErrMsg());
        } else {
            response.setErrorInfo(ErrorCode.SERVER_ERROR.getCode(), exception.getMessage());
        }
    }
}
