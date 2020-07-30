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
    
    private String ackId;
    
    private boolean success;
    
    @Override
    public String getType() {
        return RequestTypeConstants.PUSH_ACK;
    }
    
    /**
     * build push ack request.
     *
     * @param ackId ackid.
     * @return request.
     */
    public static PushAckRequest build(String ackId, boolean success) {
        PushAckRequest request = new PushAckRequest();
        request.ackId = ackId;
        request.success = success;
        return request;
    }
    
    /**
     * Getter method for property <tt>ackId</tt>.
     *
     * @return property value of ackId
     */
    public String getAckId() {
        return ackId;
    }
    
    /**
     * Setter method for property <tt>ackId</tt>.
     *
     * @param ackId value to be assigned to property ackId
     */
    public void setAckId(String ackId) {
        this.ackId = ackId;
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
}
