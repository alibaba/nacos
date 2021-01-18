/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.exception;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;

/**
 * Nacos Exception.
 *
 * @author Nacos
 */
public class NacosException extends Exception {
    
    /**
     * serialVersionUID.
     */
    private static final long serialVersionUID = -3913902031489277776L;
    
    private int errCode;
    
    private String errMsg;
    
    private Throwable causeThrowable;
    
    public NacosException() {
    }
    
    public NacosException(final int errCode, final String errMsg) {
        super(errMsg);
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
    
    public NacosException(final int errCode, final Throwable throwable) {
        super(throwable);
        this.errCode = errCode;
        this.setCauseThrowable(throwable);
    }
    
    public NacosException(final int errCode, final String errMsg, final Throwable throwable) {
        super(errMsg, throwable);
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.setCauseThrowable(throwable);
    }
    
    public int getErrCode() {
        return this.errCode;
    }
    
    public String getErrMsg() {
        if (!StringUtils.isBlank(this.errMsg)) {
            return this.errMsg;
        }
        if (this.causeThrowable != null) {
            return this.causeThrowable.getMessage();
        }
        return Constants.NULL;
    }
    
    public void setErrCode(final int errCode) {
        this.errCode = errCode;
    }
    
    public void setErrMsg(final String errMsg) {
        this.errMsg = errMsg;
    }
    
    public void setCauseThrowable(final Throwable throwable) {
        this.causeThrowable = this.getCauseThrowable(throwable);
    }
    
    private Throwable getCauseThrowable(final Throwable t) {
        if (t.getCause() == null) {
            return t;
        }
        return this.getCauseThrowable(t.getCause());
    }
    
    @Override
    public String toString() {
        return "ErrCode:" + getErrCode() + ", ErrMsg:" + getErrMsg();
    }
    
    /*
     * client error code.
     * -400 -503 throw exception to user.
     */
    
    /**
     * invalid param（参数错误）.
     */
    public static final int CLIENT_INVALID_PARAM = -400;
    
    /**
     * client disconnect.
     */
    public static final int CLIENT_DISCONNECT = -401;
    
    /**
     * over client threshold（超过client端的限流阈值）.
     */
    public static final int CLIENT_OVER_THRESHOLD = -503;
    
    /*
     * server error code.
     * 400 403 throw exception to user
     * 500 502 503 change ip and retry
     */
    
    /**
     * invalid param（参数错误）.
     */
    public static final int INVALID_PARAM = 400;
    
    /**
     * no right（鉴权失败）.
     */
    public static final int NO_RIGHT = 403;
    
    /**
     * not found.
     */
    public static final int NOT_FOUND = 404;
    
    /**
     * conflict（写并发冲突）.
     */
    public static final int CONFLICT = 409;
    
    /**
     * server error（server异常，如超时）.
     */
    public static final int SERVER_ERROR = 500;
    
    /**
     * bad gateway（路由异常，如nginx后面的Server挂掉）.
     */
    public static final int BAD_GATEWAY = 502;
    
    /**
     * over threshold（超过server端的限流阈值）.
     */
    public static final int OVER_THRESHOLD = 503;
    
    /**
     * Server is not started.
     */
    public static final int INVALID_SERVER_STATUS = 300;
    
    /**
     * Connection is not registered.
     */
    public static final int UN_REGISTER = 301;
    
    /**
     * No Handler Found.
     */
    public static final int NO_HANDLER = 302;
    
    public static final int RESOURCE_NOT_FOUND = -404;
    
    /**
     * http client error code, ome exceptions that occurred when the use the Nacos RestTemplate and Nacos
     * AsyncRestTemplate.
     */
    public static final int HTTP_CLIENT_ERROR_CODE = -500;
    
    
}
