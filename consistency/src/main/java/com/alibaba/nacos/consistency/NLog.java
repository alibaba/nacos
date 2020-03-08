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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public class NLog implements Log {

    private static final long serialVersionUID = 2277124615731537462L;

    protected String biz;
    protected String key;
    protected byte[] data;
    protected String className;
    protected String operation;
    protected Map<String, String> extendInfo = new HashMap<>(3);

    // Only this node knows that it is used to transparently transmit information at this node

    protected transient Map<String, Object> localContext = new HashMap<>(3);

    public static NLogBuilder builder() {
        return new NLogBuilder();
    }

    public void setExtendInfo(Map<String, String> extendInfo) {
        this.extendInfo = extendInfo;
    }

    @Override
    public String getBiz() {
        return biz;
    }

    @Override
    public void setBiz(String biz) {
        this.biz = biz;
    }

    @Override
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    @Override
    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
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

    @Override
    public Map<String, String> listExtendInfo() {
        return this.extendInfo;
    }

    public void addContextValue(String key, Object value) {
        this.localContext.put(key, value);
    }

    public <D> D getContextValue(String key) {
        return (D) this.localContext.get(key);
    }

    @Override
    public Map<String, Object> getLocalContext() {
        return localContext;
    }

    @Override
    public void setLocalContext(Map<String, Object> localContext) {
        this.localContext = localContext;
    }

    @Override
    public String toString() {
        return "NLog{" +
                "biz='" + biz + '\'' +
                ", key='" + key + '\'' +
                ", data=" + Arrays.toString(data) +
                ", className='" + className + '\'' +
                ", operation='" + operation + '\'' +
                ", extendInfo=" + extendInfo +
                ", localContext=" + localContext +
                '}';
    }

    public static final class NLogBuilder {
        private String biz;
        private String key;
        private byte[] data;
        private String className;
        private String operation;
        private Map<String, String> extendInfo = new HashMap<>(3);
        private transient Map<String, Object> localContext = new HashMap<>(3);

        private NLogBuilder() {
        }

        public NLogBuilder biz(String biz) {
            this.biz = biz;
            return this;
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

        public NLogBuilder addContextValue(String key, Object value) {
            this.localContext.put(key, value);
            return this;
        }

        public NLog build() {
            NLog nLog = new NLog();
            nLog.setBiz(biz);
            nLog.setKey(key);
            nLog.setData(data);
            nLog.setClassName(className);
            nLog.setOperation(operation);
            nLog.setExtendInfo(extendInfo);
            nLog.localContext = this.localContext;
            return nLog;
        }
    }
}
