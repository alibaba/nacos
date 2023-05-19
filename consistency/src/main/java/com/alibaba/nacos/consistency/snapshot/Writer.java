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
import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot write interface.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Writer {
    
    private final Map<String, LocalFileMeta> files = new HashMap<>();
    
    private String path;
    
    public Writer(String path) {
        this.path = path;
    }
    
    public String getPath() {
        return path;
    }
    
    /**
     * Adds a snapshot file without metadata.
     *
     * @param fileName file name
     * @return true on success
     */
    public boolean addFile(final String fileName) {
        files.put(fileName, new LocalFileMeta().append("file-name", fileName));
        return true;
    }
    
    /**
     * Adds a snapshot file with metadata.
     *
     * @param fileName file name
     * @return true on success
     */
    public boolean addFile(final String fileName, final LocalFileMeta meta) {
        files.put(fileName, meta);
        return true;
    }
    
    /**
     * Remove a snapshot file.
     *
     * @param fileName file name
     * @return true on success
     */
    public boolean removeFile(final String fileName) {
        files.remove(fileName);
        return true;
    }
    
    public Map<String, LocalFileMeta> listFiles() {
        return Collections.unmodifiableMap(files);
    }
    
}
