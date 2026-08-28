/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.ai.service.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounded page of canonical resources used by index reconciliation.
 *
 * @author nacos
 */
public class AiResourceIndexSourcePage {
    
    private final List<AiResourceIndexSource> items;
    
    private final boolean hasMore;
    
    public AiResourceIndexSourcePage(List<AiResourceIndexSource> items, boolean hasMore) {
        this.items = items == null || items.isEmpty() ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(items));
        this.hasMore = hasMore;
    }
    
    public List<AiResourceIndexSource> getItems() {
        return items;
    }
    
    public boolean hasMore() {
        return hasMore;
    }
}
