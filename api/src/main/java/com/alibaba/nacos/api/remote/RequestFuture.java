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
 * future for request
 * @author liuzunfei
 * @version $Id: RequestFuture.java, v 0.1 2020年09月01日 6:31 PM liuzunfei Exp $
 */
public interface RequestFuture {
    
    /**
     * @return
     */
    boolean isDone();
    
    /**
     * @return
     * @throws TimeoutException
     * @throws InterruptedException
     */
    Response get() throws TimeoutException, InterruptedException;
    
    /**
     * @param timeout
     * @return
     * @throws TimeoutException
     * @throws RemotingException
     * @throws InterruptedException
     */
    Response get(long timeout) throws TimeoutException, InterruptedException;
    
}
