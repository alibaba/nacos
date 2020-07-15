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

package com.alibaba.nacos.api.remote.response;

/**
 * abstract response model via rpc channel.
 *
 * @author liuzunfei
 * @version $Id: Response.java, v 0.1 2020年07月13日 6:03 PM liuzunfei Exp $
 */
public abstract class Response {
    
    int resultCode;
    
    int errorCode;
    
    String message;
    
    String type;
    
    /**
     * Check Response  is Successd.
     * @return
     */
    public boolean isSuccess() {
        return this.resultCode == ResponseCode.SUCCESS.getCode();
    }
    
    public Response() {
    
    }
    
    public Response(String type, int resultCode, String message) {
        this.type = type;
        this.resultCode = resultCode;
        this.message = message;
    }
    
    /**
     * Getter method for property <tt>resultCode</tt>.
     *
     * @return property value of resultCode
     */
    public int getResultCode() {
        return resultCode;
    }
    
    /**
     * Setter method for property <tt>resultCode</tt>.
     *
     * @param resultCode value to be assigned to property resultCode
     */
    public void setResultCode(int resultCode) {
        this.resultCode = resultCode;
    }
    
    /**
     * Getter method for property <tt>message</tt>.
     *
     * @return property value of message
     */
    public String getMessage() {
        return message;
    }
    
    /**
     * Setter method for property <tt>message</tt>.
     *
     * @param message value to be assigned to property message
     */
    public void setMessage(String message) {
        this.message = message;
    }
    
    /**
     * Getter method for property <tt>errorCode</tt>.
     *
     * @return property value of errorCode
     */
    public int getErrorCode() {
        return errorCode;
    }
    
    /**
     * Setter method for property <tt>errorCode</tt>.
     *
     * @param errorCode value to be assigned to property errorCode
     */
    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }
    
    /**
     * Getter method for property <tt>type</tt>.
     *
     * @return property value of type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Setter method for property <tt>type</tt>.
     *
     * @param type value to be assigned to property type
     */
    public void setType(String type) {
        this.type = type;
    }
    
}
