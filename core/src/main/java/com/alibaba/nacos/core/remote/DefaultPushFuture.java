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

package com.alibaba.nacos.core.remote;

import com.alibaba.nacos.api.remote.response.PushCallBack;

import java.util.concurrent.TimeoutException;

/**
 * default push future.
 *
 * @author liuzunfei
 * @version $Id: DefaultPushFuture.java, v 0.1 2020年08月12日 7:10 PM liuzunfei Exp $
 */
public class DefaultPushFuture implements PushFuture {
    
    private long timeStamp;
    
    private volatile boolean isDone = false;
    
    private boolean isSuccess;
    
    private PushCallBack pushCallBack;
    
    private Exception exception;
    
    private String requestId;
    
    /**
     * Getter method for property <tt>pushCallBack</tt>.
     *
     * @return property value of pushCallBack
     */
    public PushCallBack getPushCallBack() {
        return pushCallBack;
    }
    
    /**
     * Getter method for property <tt>timeStamp</tt>.
     *
     * @return property value of timeStamp
     */
    public long getTimeStamp() {
        return timeStamp;
    }
    
    public DefaultPushFuture() {
    }
    
    public DefaultPushFuture(String requestId) {
        this(requestId, null);
    }
    
    public DefaultPushFuture(String requestId, PushCallBack pushCallBack) {
        this.timeStamp = System.currentTimeMillis();
        this.pushCallBack = pushCallBack;
        this.requestId = requestId;
    }
    
    public void setSuccessResult() {
        isDone = true;
        isSuccess = true;
        synchronized (this) {
            notifyAll();
        }
        
        if (pushCallBack != null) {
            pushCallBack.onSuccess();
        }
    }
    
    public void setFailResult(Exception e) {
        isDone = true;
        isSuccess = false;
        synchronized (this) {
            notifyAll();
        }
        
        if (pushCallBack != null) {
            pushCallBack.onFail(e);
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
    public boolean get() throws TimeoutException, InterruptedException {
        synchronized (this) {
            while (!isDone) {
                wait();
            }
        }
        return isSuccess;
    }
    
    @Override
    public boolean get(long timeout) throws TimeoutException, InterruptedException {
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
            return isSuccess;
        } else {
            throw new TimeoutException();
        }
    }
}
