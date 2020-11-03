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

package com.alibaba.nacos.consistency.snapshot;

import java.util.Properties;

/**
 * Meta information for the snapshot file.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class LocalFileMeta {
    
    private final Properties fileMeta;
    
    public LocalFileMeta() {
        this.fileMeta = new Properties();
    }
    
    public LocalFileMeta(Properties properties) {
        this.fileMeta = properties;
    }
    
    public LocalFileMeta append(Object key, Object value) {
        fileMeta.put(key, value);
        return this;
    }
    
    public Object get(String key) {
        return fileMeta.getProperty(key);
    }
    
    public Properties getFileMeta() {
        return fileMeta;
    }
    
    @Override
    public String toString() {
        return "LocalFileMeta{" + "fileMeta=" + fileMeta + '}';
    }
}
