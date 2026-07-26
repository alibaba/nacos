/*
 * Copyright 1999-2020 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.remote.request;

/**
 * push ack request.
 *
 * @author liuzunfei
 * @version $Id: PushAckRequest.java, v 0.1 2020年07月29日 8:25 PM liuzunfei Exp $
 */
public class PushAckRequest extends InternalRequest {
    
    private String requestId;
    
    private boolean success;
    
    private Exception exception;
    
    /**
     * build push ack request.
     *
     * @param requestId requestId.
     * @return request.
     */
    public static PushAckRequest build(String requestId, boolean success) {
        PushAckRequest request = new PushAckRequest();
        request.requestId = requestId;
        request.success = success;
        return request;
    }
    
    /**
     * Getter method for property <tt>requestId</tt>.
     *
     * @return property value of requestId
     */
    @Override
    public String getRequestId() {
        return requestId;
    }
    
    /**
     * Setter method for property <tt>requestId</tt>.
     *
     * @param requestId value to be assigned to property requestId
     */
    @Override
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    /**
     * Getter method for property <tt>success</tt>.
     *
     * @return property value of success
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Setter method for property <tt>success</tt>.
     *
     * @param success value to be assigned to property success
     */
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    /**
     * Setter method for property <tt>exception</tt>.
     *
     * @param exception value to be assigned to property exception
     */
    public void setException(Exception exception) {
        this.exception = exception;
    }
    
    /**
     * Getter method for property <tt>exception</tt>.
     *
     * @return property value of exception
     */
    public Exception getException() {
        return exception;
    }
}
