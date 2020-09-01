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

package com.alibaba.nacos.api.remote;

import com.alibaba.nacos.api.remote.response.Response;

import java.util.concurrent.TimeoutException;

/**
 * default request future.
 *
 * @author liuzunfei
 * @version $Id: DefaultRequestFuture.java, v 0.1 2020年09月01日 6:42 PM liuzunfei Exp $
 */
public class DefaultRequestFuture implements RequestFuture {
    
    private long timeStamp;
    
    private volatile boolean isDone = false;
    
    private boolean isSuccess;
    
    private RequestCallBack requestCallBack;
    
    private Exception exception;
    
    private String requestId;
    
    private Response response;
    
    /**
     * Getter method for property <tt>requestCallBack</tt>.
     *
     * @return property value of requestCallBack
     */
    public RequestCallBack getRequestCallBack() {
        return requestCallBack;
    }
    
    /**
     * Getter method for property <tt>timeStamp</tt>.
     *
     * @return property value of timeStamp
     */
    public long getTimeStamp() {
        return timeStamp;
    }
    
    public DefaultRequestFuture() {
    }
    
    public DefaultRequestFuture(String requestId) {
        this(requestId, null);
    }
    
    public DefaultRequestFuture(String requestId, RequestCallBack requestCallBack) {
        this.timeStamp = System.currentTimeMillis();
        this.requestCallBack = requestCallBack;
        this.requestId = requestId;
    }
    
    public void setResponse(Response response) {
        isDone = true;
        this.response = response;
        this.isSuccess = response.isSuccess();
        synchronized (this) {
            notifyAll();
        }
        
        if (requestCallBack != null) {
            requestCallBack.onResponse(response);
        }
    }
    
    public void setFailResult(Exception e) {
        isDone = true;
        isSuccess = false;
        synchronized (this) {
            notifyAll();
        }
        
        if (requestCallBack != null) {
            requestCallBack.onException(e);
        }
    }
    
    public String getRequestId() {
        return this.requestId;
    }
    
    @Override
    public boolean isDone() {
        return isDone;
    }
    
    @Override
    public Response get() throws TimeoutException, InterruptedException {
        synchronized (this) {
            while (!isDone) {
                wait();
            }
        }
        return response;
    }
    
    @Override
    public Response get(long timeout) throws TimeoutException, InterruptedException {
        if (timeout < 0) {
            synchronized (this) {
                while (!isDone) {
                    wait();
                }
            }
        } else if (timeout > 0) {
            long end = System.currentTimeMillis() + timeout;
            long waitTime = timeout;
            synchronized (this) {
                while (!isDone && waitTime > 0) {
                    wait(waitTime);
                    waitTime = end - System.currentTimeMillis();
                }
            }
        }
        
        if (isDone) {
            return response;
        } else {
            throw new TimeoutException();
        }
    }
}
