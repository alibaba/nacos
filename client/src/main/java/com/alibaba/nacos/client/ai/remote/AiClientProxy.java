/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.ai.remote;

import com.alibaba.nacos.api.ai.model.prompt.Prompt;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.common.lifecycle.Closeable;

/**
 * AI client proxy interface for abstracting transport layer (gRPC / HTTP).
 *
 * <p>Defines AI operations that support switching between gRPC and HTTP transport.</p>
 *
 * @author nacos
 */
public interface AiClientProxy extends Closeable {
    
    /**
     * Query prompt by latest/version/label with optional md5 for conditional query.
     *
     * @param promptKey prompt key
     * @param version   prompt version, optional
     * @param label     prompt label, optional
     * @param md5       client md5 for conditional query, optional
     * @return prompt detail
     * @throws NacosException if request parameter is invalid or handle error
     */
    Prompt queryPrompt(String promptKey, String version, String label, String md5)
            throws NacosException;
}
