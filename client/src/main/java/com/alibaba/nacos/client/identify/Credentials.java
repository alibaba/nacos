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
package com.alibaba.nacos.client.identify;

/**
 * Credentials
 *
 * @author Nacos
 */
public class Credentials implements SpasCredential {

    private volatile String accessKey;

    private volatile String secretKey;

    public Credentials(String accessKey, String secretKey) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    public Credentials() {
        this(null, null);
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean valid() {
        return accessKey != null && !accessKey.isEmpty() && secretKey != null && !secretKey.isEmpty();
    }

    public boolean identical(Credentials other) {
        return this == other ||
            (other != null &&
                (accessKey == null && other.accessKey == null || accessKey != null && accessKey.equals(other.accessKey))
                &&
                (secretKey == null && other.secretKey == null || secretKey != null && secretKey.equals(
                    other.secretKey)));
    }
}
