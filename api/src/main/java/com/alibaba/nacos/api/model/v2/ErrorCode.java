/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.model.v2;

/**
 * Response Error Code.
 *
 * @author dongyafei
 * @date 2022/7/22
 */

public enum ErrorCode {
    
    /**
     *  success.
     */
    SUCCESS(0, "success"),
    
    /**
     *  parameter missing.
     */
    PARAMETER_MISSING(10000, "parameter missing"),
    
    /**
     *  access denied.
     */
    ACCESS_DENIED(10001, "access denied"),
    
    /**
     *  data access error.
     */
    DATA_ACCESS_ERROR(10002, "data access error"),
    
    /**
     *  'tenant' parameter error.
     */
    TENANT_PARAM_ERROR(20001, "'tenant' parameter error"),
    
    /**
     *  parameter validate error.
     */
    PARAMETER_VALIDATE_ERROR(20002, "parameter validate error"),
    
    /**
     *  MediaType Error.
     */
    MEDIA_TYPE_ERROR(20003, "MediaType Error"),
    
    /**
     *  resource not found.
     */
    RESOURCE_NOT_FOUND(20004, "resource not found"),
    
    /**
     *  resource conflict.
     */
    RESOURCE_CONFLICT(20005, "resource conflict"),
    
    /**
     *  config listener is null.
     */
    CONFIG_LISTENER_IS_NULL(20006, "config listener is null"),
    
    /**
     *  config listener error.
     */
    CONFIG_LISTENER_ERROR(20007, "config listener error"),
    
    /**
     *  invalid dataId.
     */
    INVALID_DATA_ID(20008, "invalid dataId"),
    
    /**
     *  parameter mismatch.
     */
    PARAMETER_MISMATCH(20009, "parameter mismatch"),
    
    /**
     *  service name error.
     */
    SERVICE_NAME_ERROR(21000, "service name error"),
    
    /**
     *  weight error.
     */
    WEIGHT_ERROR(21001, "weight error"),
    
    /**
     *  instance metadata error.
     */
    INSTANCE_METADATA_ERROR(21002, "instance metadata error"),
    
    /**
     *  instance not found.
     */
    INSTANCE_NOT_FOUND(21003, "instance not found"),
    
    /**
     *  instance error.
     */
    INSTANCE_ERROR(21004, "instance error"),
    
    /**
     *  service metadata error.
     */
    SERVICE_METADATA_ERROR(21005, "service metadata error"),
    
    /**
     *  selector error.
     */
    SELECTOR_ERROR(21006, "selector error"),
    
    /**
     *  service already exist.
     */
    SERVICE_ALREADY_EXIST(21007, "service already exist"),
    
    /**
     *  service not exist.
     */
    SERVICE_NOT_EXIST(21008, "service not exist"),
    
    /**
     *  service delete failure.
     */
    SERVICE_DELETE_FAILURE(21009, "service delete failure"),
    
    /**
     *  healthy param miss.
     */
    HEALTHY_PARAM_MISS(21010, "healthy param miss"),
    
    /**
     *  health check still running.
     */
    HEALTH_CHECK_STILL_RUNNING(21011, "health check still running"),
    
    /**
     *  illegal namespace.
     */
    ILLEGAL_NAMESPACE(22000, "illegal namespace"),
    
    /**
     *  namespace not exist.
     */
    NAMESPACE_NOT_EXIST(22001, "namespace not exist"),
    
    /**
     *  namespace already exist.
     */
    NAMESPACE_ALREADY_EXIST(22002, "namespace already exist"),
    
    /**
     *  illegal state.
     */
    ILLEGAL_STATE(23000, "illegal state"),
    
    /**
     *  node info error.
     */
    NODE_INFO_ERROR(23001, "node info error"),
    
    /**
     *  node down failure.
     */
    NODE_DOWN_FAILURE(23001, "node down failure"),
    
    /**
     *  server error.
     */
    SERVER_ERROR(30000, "server error");
    
    private final Integer code;
    
    private final String msg;
    
    public Integer getCode() {
        return code;
    }
    
    public String getMsg() {
        return msg;
    }
    
    ErrorCode(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
