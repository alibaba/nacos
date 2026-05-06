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

package com.alibaba.nacos.api.ai.model.mcp;

import java.io.Serializable;
import java.util.Map;

/**
 * Encrypted payload wrapper for MCP tool specification.
 * Holds ciphertext and encryption metadata (algorithm, iv, keyId, version, etc.).
 * @author luoxiner
 */
public class EncryptObject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * The ciphertext or encoded payload.
     */
    private String data;

    /**
     * Additional encryption metadata, e.g. alg, iv, keyId, version.
     */
    private Map<String, String> encryptInfo;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public Map<String, String> getEncryptInfo() {
        return encryptInfo;
    }

    public void setEncryptInfo(Map<String, String> encryptInfo) {
        this.encryptInfo = encryptInfo;
    }
}
