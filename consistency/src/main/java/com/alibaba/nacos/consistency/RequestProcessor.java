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

package com.alibaba.nacos.consistency;

import com.alibaba.nacos.consistency.entity.ReadRequest;
import com.alibaba.nacos.consistency.entity.Response;
import com.alibaba.nacos.consistency.entity.WriteRequest;

/**
 * Can be discovered through SPI or Spring, This interface is just a function definition interface. Different
 * consistency protocols have their own LogDispatcher. It is not recommended to directly implement this interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("PMD.AbstractClassShouldStartWithAbstractNamingRule")
public abstract class RequestProcessor {
    
    /**
     * get data by key.
     *
     * @param request request {@link com.alibaba.nacos.consistency.entity.ReadRequest}
     * @return target type data
     */
    public abstract Response onRequest(ReadRequest request);
    
    /**
     * Process Submitted Log.
     *
     * @param log {@link WriteRequest}
     * @return {@link boolean}
     */
    public abstract Response onApply(WriteRequest log);
    
    /**
     * Irremediable errors that need to trigger business price cuts.
     *
     * @param error {@link Throwable}
     */
    public void onError(Throwable error) {
    }
    
    /**
     * In order for the state machine that handles the transaction to be able to route the Log to the correct
     * LogProcessor, the LogProcessor needs to have an identity information.
     *
     * @return Business unique identification name
     */
    public abstract String group();
    
}
