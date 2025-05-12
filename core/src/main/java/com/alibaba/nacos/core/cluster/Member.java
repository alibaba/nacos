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

import com.alibaba.nacos.api.common.NodeState;
import com.alibaba.nacos.api.model.response.NacosMember;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.core.utils.Loggers;
import com.alibaba.nacos.sys.env.EnvUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Cluster member node.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public class Member extends NacosMember implements Comparable<Member>, Cloneable, Serializable {
    
    private static final long serialVersionUID = -6061130045021268736L;
    
    private transient int failAccessCnt = 0;
    
    /**
     * After 2.3 version, all server request will use grpc default.
     *
     * @deprecated will be deprecated after server not support upgrade from lower 2.3.
     */
    @Deprecated
    private boolean grpcReportEnabled;
    
    public Member() {
        String prefix = "nacos.core.member.meta.";
        super.getExtendInfo().put(MemberMetaDataConstants.SITE_KEY,
                EnvUtil.getProperty(prefix + MemberMetaDataConstants.SITE_KEY, "unknow"));
        super.getExtendInfo().put(MemberMetaDataConstants.AD_WEIGHT,
                EnvUtil.getProperty(prefix + MemberMetaDataConstants.AD_WEIGHT, "0"));
        super.getExtendInfo()
                .put(MemberMetaDataConstants.WEIGHT, EnvUtil.getProperty(prefix + MemberMetaDataConstants.WEIGHT, "1"));
    }
    
    public boolean isGrpcReportEnabled() {
        return grpcReportEnabled;
    }
    
    public void setGrpcReportEnabled(boolean grpcReportEnabled) {
        this.grpcReportEnabled = grpcReportEnabled;
    }
    
    public static MemberBuilder builder() {
        return new MemberBuilder();
    }
    
    public Object getExtendVal(String key) {
        return getExtendInfo().get(key);
    }
    
    public void setExtendVal(String key, Object value) {
        getExtendInfo().put(key, value);
    }
    
    public void delExtendVal(String key) {
        getExtendInfo().remove(key);
    }
    
    public boolean check() {
        return StringUtils.isNoneBlank(getIp(), getAddress()) && getPort() != -1;
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
        return super.equals(that);
    }
    
    @Override
    public int compareTo(Member o) {
        return getAddress().compareTo(o.getAddress());
    }
    
    /**
     * get a copy.
     *
     * @return member.
     */
    public Member copy() {
        Member copy = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this);
            // convert the input stream to member object
            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            ObjectInputStream ois = new ObjectInputStream(bais);
            copy = (Member) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Loggers.CORE.warn("[Member copy] copy failed", e);
        }
        return copy;
    }
    
    public static final class MemberBuilder {
        
        private String ip;
        
        private int port;
        
        private NodeState state;
        
        private Map<String, String> extendInfo = Collections.synchronizedMap(new TreeMap<>());
        
        private MemberBuilder() {
        }
        
        public MemberBuilder ip(String ip) {
            this.ip = ip;
            return this;
        }
        
        public MemberBuilder port(int port) {
            this.port = port;
            return this;
        }
        
        public MemberBuilder state(NodeState state) {
            this.state = state;
            return this;
        }
        
        public MemberBuilder extendInfo(Map<String, String> extendInfo) {
            this.extendInfo.putAll(extendInfo);
            return this;
        }
        
        /**
         * build Member.
         *
         * @return {@link Member}
         */
        public Member build() {
            Member serverNode = new Member();
            if (Objects.nonNull(this.extendInfo)) {
                serverNode.getExtendInfo().putAll(this.extendInfo);
            }
            serverNode.setState(this.state);
            serverNode.setIp(this.ip);
            serverNode.setPort(this.port);
            serverNode.setAddress(this.ip + ":" + this.port);
            return serverNode;
        }
    }
    
}
