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

package com.alibaba.nacos.core.distributed;

import java.util.Collections;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NDatum implements Datum {

    private String key;
    private byte[] data;
    private String className;
    private String operation;
    private Map<String, String> extendInfo = Collections.emptyMap();

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getOperation() {
        return operation;
    }

    @Override
    public String extendVal(String key) {
        return extendInfo.get(key);
    }

    public static NDatumBuilder builder() {
        return new NDatumBuilder();
    }

    public static final class NDatumBuilder {
        private String key;
        private byte[] data;
        private String className;
        private String operation;
        private Map<String, String> extendInfo = Collections.emptyMap();

        private NDatumBuilder() {
        }

        public NDatumBuilder key(String key) {
            this.key = key;
            return this;
        }

        public NDatumBuilder data(byte[] data) {
            this.data = data;
            return this;
        }

        public NDatumBuilder className(String className) {
            this.className = className;
            return this;
        }

        public NDatumBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public NDatumBuilder extendInfo(Map<String, String> extendInfo) {
            this.extendInfo = extendInfo;
            return this;
        }

        public NDatum build() {
            NDatum nDatum = new NDatum();
            nDatum.extendInfo = this.extendInfo;
            nDatum.className = this.className;
            nDatum.key = this.key;
            nDatum.data = this.data;
            nDatum.operation = this.operation;
            return nDatum;
        }
    }
}
