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

package com.alibaba.nacos.core.cluster;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.alibaba.fastjson.annotation.JSONField;
import org.apache.commons.lang3.StringUtils;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Member {

    private String ip;

    private int port = -1;

    private NodeState state = NodeState.UP;

    private Map<String, Object> extendInfo = new HashMap<>();

    private String address = "";

    @JSONField(serialize = false)
    private transient int failAccessCnt = 0;

    public Member() {
        extendInfo.put(MemberMetaDataConstants.SITE_KEY, "unknown");
        extendInfo.put(MemberMetaDataConstants.AD_WEIGHT, "0");
        extendInfo.put(MemberMetaDataConstants.WEIGHT, "1");
        extendInfo.put(MemberMetaDataConstants.DISTRO_BEATS, null);
    }

    public static ServerNodeBuilder builder() {
        return new ServerNodeBuilder();
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public Map<String, Object> getExtendInfo() {
        return extendInfo;
    }

    public void setExtendInfo(Map<String, Object> extendInfo) {
        this.extendInfo = extendInfo;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIp() {
        return ip;
    }

    public String getAddress() {
        if (StringUtils.isBlank(address)) {
            address = ip + ":" + port;
        }
        return address;
    }

    public Object getExtendVal(String key) {
        return extendInfo.get(key);
    }

    public void setExtendVal(String key, Object value) {
        extendInfo.put(key, value);
    }

    public boolean check() {
        return StringUtils.isNoneBlank(ip, address) && port != -1;
    }

    public int getFailAccessCnt() {
        return failAccessCnt;
    }

    public void setFailAccessCnt(int failAccessCnt) {
        this.failAccessCnt = failAccessCnt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Member that = (Member) o;
        if (StringUtils.isAnyBlank(address, that.address)) {
            return port == that.port &&
                    Objects.equals(ip, that.ip);
        }
        return StringUtils.equals(address, that.address);
    }

    @Override
    public String toString() {
        return "Member{" +
                "address='" + address + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }

    public static final class ServerNodeBuilder {
        private String ip;
        private int port;
        private NodeState state;
        private Map<String, String> extendInfo;

        private ServerNodeBuilder() {
        }

        public ServerNodeBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }

        public ServerNodeBuilder port(int port) {
            this.port = port;
            return this;
        }

        public ServerNodeBuilder state(NodeState state) {
            this.state = state;
            return this;
        }

        public ServerNodeBuilder extendInfo(Map<String, String> extendInfo) {
            this.extendInfo = extendInfo;
            return this;
        }

        public Member build() {
            Member serverNode = new Member();
            serverNode.extendInfo.putAll(this.extendInfo);
            serverNode.state = this.state;
            serverNode.ip = this.ip;
            serverNode.port = this.port;
            serverNode.address = this.ip + ":" + this.port;
            return serverNode;
        }
    }

}
