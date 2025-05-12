/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.config.server.model;

/**
 * The type Config listen state.
 *
 * @author Sunrisea
 */
public class ConfigListenState {
    
    private String md5;
    
    private boolean namespaceTransfer;
    
    public ConfigListenState(String md5) {
        this.md5 = md5;
    }
    
    /**
     * Is namespace transfer boolean.
     *
     * @return the boolean
     */
    public boolean isNamespaceTransfer() {
        return namespaceTransfer;
    }
    
    /**
     * Sets namespace transfer.
     *
     * @param namespaceTransfer the namespace transfer
     */
    public void setNamespaceTransfer(boolean namespaceTransfer) {
        this.namespaceTransfer = namespaceTransfer;
    }
    
    /**
     * Gets md 5.
     *
     * @return the md 5
     */
    public String getMd5() {
        return md5;
    }
    
    /**
     * Sets md 5.
     *
     * @param md5 the md 5
     */
    public void setMd5(String md5) {
        this.md5 = md5;
    }
}
