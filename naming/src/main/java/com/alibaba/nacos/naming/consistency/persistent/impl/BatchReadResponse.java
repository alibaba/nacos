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

package com.alibaba.nacos.naming.consistency.persistent.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Batch read response.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class BatchReadResponse implements Serializable {
    
    private static final long serialVersionUID = 5639813122389207205L;
    
    private List<byte[]> keys = new ArrayList<>(16);
    
    private List<byte[]> values = new ArrayList<>(16);
    
    public List<byte[]> getKeys() {
        return keys;
    }
    
    public void setKeys(List<byte[]> keys) {
        this.keys = keys;
    }
    
    public List<byte[]> getValues() {
        return values;
    }
    
    public void setValues(List<byte[]> values) {
        this.values = values;
    }
    
    public void append(byte[] key, byte[] value) {
        keys.add(key);
        values.add(value);
    }
}
