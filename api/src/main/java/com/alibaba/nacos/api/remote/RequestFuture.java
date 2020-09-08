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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * future for request.
 *
 * @author liuzunfei
 * @version $Id: RequestFuture.java, v 0.1 2020年09月01日 6:31 PM liuzunfei Exp $
 */
public interface RequestFuture {
    
    /**
     * check that it is done or not..
     * @return
     */
    boolean isDone();
    
    /**
     * get response without timeouts.
     * @return return response if done.
     * @throws InterruptedException  throws if interruted.
     * @throws ExecutionException throw if execute fail.
     */
    Response get() throws InterruptedException, ExecutionException;
    
    /**
     * get response with a given timeouts.
     * @param timeout timeout millseconds.
     * @return return response if done.
     * @throws TimeoutException throws if timeout.
     * @throws ExecutionException throw if execute fail.
     * @throws InterruptedException  throws if interruted.
     */
    Response get(long timeout) throws TimeoutException, InterruptedException, ExecutionException;
    
}
