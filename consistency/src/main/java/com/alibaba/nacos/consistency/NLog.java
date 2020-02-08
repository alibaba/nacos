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

package com.alibaba.nacos.consistency;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class NLog implements Log {

    private static final long serialVersionUID = 2277124615731537462L;

    private String key;
    private byte[] data;
    private String className;
    private String operation;
    private Map<String, String> extendInfo = new HashMap<>(3);

    public void setKey(String key) {
        this.key = key;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public void setExtendInfo(Map<String, String> extendInfo) {
        this.extendInfo = extendInfo;
    }

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

    @Override
    public void addExtendVal(String key, String val) {
        extendInfo.put(key, val);
    }

    @Override
    public void appendExtendInfo(Map<String, String> extendInfo) {
        this.extendInfo.putAll(extendInfo);
    }

    private static NLogBuilder builder() {
        return new NLogBuilder();
    }

    public static final class NLogBuilder {
        private String key;
        private byte[] data;
        private String className;
        private String operation;
        private Map<String, String> extendInfo = Collections.emptyMap();

        private NLogBuilder() {
        }

        public NLogBuilder key(String key) {
            this.key = key;
            return this;
        }

        public NLogBuilder data(byte[] data) {
            this.data = data;
            return this;
        }

        public NLogBuilder className(String className) {
            this.className = className;
            return this;
        }

        public NLogBuilder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public NLogBuilder extendInfo(Map<String, String> extendInfo) {
            this.extendInfo = extendInfo;
            return this;
        }

        public NLog build() {
            NLog nDatum = new NLog();
            nDatum.extendInfo = this.extendInfo;
            nDatum.className = this.className;
            nDatum.key = this.key;
            nDatum.data = this.data;
            nDatum.operation = this.operation;
            return nDatum;
        }
    }
}
