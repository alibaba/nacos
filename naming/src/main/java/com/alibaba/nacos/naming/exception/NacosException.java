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
package com.alibaba.nacos.naming.exception;

/**
 * @author dungu.zpf
 */
public class NacosException extends Exception {

    private static final long serialVersionUID = 266495151581594848L;

    private int errorCode;

    private String errorMsg;

    public NacosException() {
        super();
    }

    public NacosException(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public NacosException(int errorCode, String errorMsg) {
        super(errorMsg);
        this.errorCode = errorCode;
        this.errorMsg = errorMsg;
    }

    public NacosException(int errorCode, String msg, Throwable cause) {
        super(msg, cause);
        this.errorCode = errorCode;
    }

    public NacosException(int errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    /**
     * server error code, use http code 400 403 throw exception to user 500 502
     * 503 change ip and retry
     */
    /**
     *  invalid param
     */
    public static final int INVALID_PARAM = 400;
    /**
     *  no right
     */
    public static final int NO_RIGHT = 403;
    /**
     *  not found
     */
    public static final int NOT_FOUND = 404;

    /**
     *  conflict
     */
    public static final int CONFLICT = 409;
    /**
     *  server error
     */
    public static final int SERVER_ERROR = 500;
    /**
     *  bad gateway
     */
    public static final int BAD_GATEWAY = 502;
    /**
     *  over threshold
     */
    public static final int OVER_THRESHOLD = 503;
}
