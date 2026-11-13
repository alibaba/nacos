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

package com.alibaba.nacos.api.ai.model.a2a;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A2A 1.0 scope list wrapper.
 *
 * @author nacos
 */
public class StringList {
    
    private List<String> list = new ArrayList<>();
    
    /**
     * Get the strings represented by this A2A wrapper.
     * @return ordered string values
     */
    public List<String> getList() {
        return list;
    }
    
    /**
     * Set the strings represented by this A2A wrapper.
     * @param list ordered string values
     */
    public void setList(List<String> list) {
        this.list = list;
    }
    
    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof StringList
            && Objects.equals(list, ((StringList) other).list);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(list);
    }
}
