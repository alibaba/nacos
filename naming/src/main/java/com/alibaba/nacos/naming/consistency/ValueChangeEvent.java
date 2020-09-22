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

package com.alibaba.nacos.naming.consistency;

import com.alibaba.nacos.common.notify.Event;
import com.alibaba.nacos.consistency.DataOperation;
import com.alibaba.nacos.naming.pojo.Record;

/**
 * The value changes events. //TODO Recipients need to implement the ability to receive batch events
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class ValueChangeEvent extends Event {
    
    private final String key;
    
    private final Record value;
    
    private final DataOperation action;
    
    public ValueChangeEvent(String key, Record value, DataOperation action) {
        this.key = key;
        this.value = value;
        this.action = action;
    }
    
    public String getKey() {
        return key;
    }
    
    public Record getValue() {
        return value;
    }
    
    public DataOperation getAction() {
        return action;
    }
    
    public static ValueChangeEventBuilder builder() {
        return new ValueChangeEventBuilder();
    }
    
    public static final class ValueChangeEventBuilder {
        
        private String key;
        
        private Record value;
        
        private DataOperation action;
        
        private ValueChangeEventBuilder() {
        }
        
        public ValueChangeEventBuilder key(String key) {
            this.key = key;
            return this;
        }
        
        public ValueChangeEventBuilder value(Record value) {
            this.value = value;
            return this;
        }
        
        public ValueChangeEventBuilder action(DataOperation action) {
            this.action = action;
            return this;
        }
        
        public ValueChangeEvent build() {
            return new ValueChangeEvent(key, value, action);
        }
    }
}
