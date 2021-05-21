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

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
    
    private String connectionId;
    
    private Response response;
    
    private ScheduledFuture timeoutFuture;
    
    TimeoutInnerTrigger timeoutInnerTrigger;
    
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
    
    public DefaultRequestFuture(String connectionId, String requestId) {
        this(connectionId, requestId, null, null);
    }
    
    public DefaultRequestFuture(String connectionId, String requestId, RequestCallBack requestCallBack,
            TimeoutInnerTrigger timeoutInnerTrigger) {
        this.timeStamp = System.currentTimeMillis();
        this.requestCallBack = requestCallBack;
        this.requestId = requestId;
        this.connectionId = connectionId;
        if (requestCallBack != null) {
            this.timeoutFuture = RpcScheduledExecutor.TIMEOUT_SCHEDULER
                    .schedule(new TimeoutHandler(), requestCallBack.getTimeout(), TimeUnit.MILLISECONDS);
        }
        this.timeoutInnerTrigger = timeoutInnerTrigger;
    }
    
    public void setResponse(final Response response) {
        isDone = true;
        this.response = response;
        this.isSuccess = response.isSuccess();
        if (this.timeoutFuture != null) {
            timeoutFuture.cancel(true);
        }
        synchronized (this) {
            notifyAll();
        }
        
        callBacInvoke();
    }
    
    public void setFailResult(Exception e) {
        isDone = true;
        isSuccess = false;
        this.exception = e;
        synchronized (this) {
            notifyAll();
        }
        
        callBacInvoke();
    }
    
    private void callBacInvoke() {
        if (requestCallBack != null) {
            if (requestCallBack.getExecutor() != null) {
                requestCallBack.getExecutor().execute(new CallBackHandler());
            } else {
                new CallBackHandler().run();
            }
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
    public Response get() throws InterruptedException {
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
            if (timeoutInnerTrigger != null) {
                timeoutInnerTrigger.triggerOnTimeout();
            }
            throw new TimeoutException("request timeout after " + timeout + " milliseconds, requestId=" + requestId);
        }
    }
    
    class CallBackHandler implements Runnable {
        
        @Override
        public void run() {
            if (exception != null) {
                requestCallBack.onException(exception);
            } else {
                requestCallBack.onResponse(response);
            }
        }
    }
    
    class TimeoutHandler implements Runnable {
        
        public TimeoutHandler() {
        }
        
        @Override
        public void run() {
            setFailResult(new TimeoutException(
                    "Timeout After " + requestCallBack.getTimeout() + " milliseconds,requestId =" + requestId));
        }
    }
    
    public interface TimeoutInnerTrigger {
        
        /**
         * triggered on timeout .
         */
        public void triggerOnTimeout();
        
    }
    
    /**
     * Getter method for property <tt>connectionId</tt>.
     *
     * @return property value of connectionId
     */
    public String getConnectionId() {
        return connectionId;
    }
    
    /**
     * Setter method for property <tt>timeoutFuture</tt>.
     *
     * @param timeoutFuture value to be assigned to property timeoutFuture
     */
    public void setTimeoutFuture(ScheduledFuture timeoutFuture) {
        this.timeoutFuture = timeoutFuture;
    }
    
}
