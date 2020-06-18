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

import java.util.Collections;
import java.util.Map;

/**
 * Read the snapshot file interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Reader {
    
    private final String path;
    
    private final Map<String, LocalFileMeta> allFiles;
    
    public Reader(String path, Map<String, LocalFileMeta> allFiles) {
        this.path = path;
        this.allFiles = Collections.unmodifiableMap(allFiles);
    }
    
    public String getPath() {
        return path;
    }
    
    public Map<String, LocalFileMeta> listFiles() {
        return allFiles;
    }
    
    public LocalFileMeta getFileMeta(String fileName) {
        return allFiles.get(fileName);
    }
}
