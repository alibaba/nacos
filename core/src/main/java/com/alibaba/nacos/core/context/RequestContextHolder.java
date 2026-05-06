/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.context;

import java.util.function.Supplier;

/**
 * Holder for request context for each worker thread.
 *
 * @author xiweng.yy
 */
public class RequestContextHolder {

    private static final Supplier<RequestContext> REQUEST_CONTEXT_FACTORY = () -> {
        long requestTimestamp = System.currentTimeMillis();
        return new RequestContext(requestTimestamp);
    };
    
    private static final ThreadLocal<RequestContext> CONTEXT_HOLDER = ThreadLocal.withInitial(REQUEST_CONTEXT_FACTORY);
    
    public static RequestContext getContext() {
        return CONTEXT_HOLDER.get();
    }
    
    public static void removeContext() {
        CONTEXT_HOLDER.remove();
    }
}
