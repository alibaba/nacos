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

package com.alibaba.nacos.copilot.connection;

import java.util.List;

/**
 * Reads model connections from one configuration source.
 *
 * <p>Implementations are discovered as Spring beans. A smaller {@link #order()} wins when
 * two sources define a connection with the same name.
 *
 * @author nacos
 */
public interface ProviderReader {
    
    /**
     * Reader priority. A smaller value wins on duplicate connection names.
     *
     * @return priority
     */
    int order();
    
    /**
     * Read the current connections from this source.
     *
     * @return connections; an empty list when the source has nothing to offer
     */
    List<CopilotModelConnection> read();
}
