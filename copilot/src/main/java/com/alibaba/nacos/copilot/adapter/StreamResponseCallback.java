/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.copilot.adapter;

/**
 * Stream response callback interface.
 *
 * @param <T> response type
 * @author nacos
 */
public interface StreamResponseCallback<T> {
    
    /**
     * Called when a new response chunk is received.
     *
     * @param response response chunk
     */
    void onNext(T response);
    
    /**
     * Called when an error occurs.
     *
     * @param t error
     */
    void onError(Throwable t);
    
    /**
     * Called when the stream is complete.
     */
    void onComplete();
}
