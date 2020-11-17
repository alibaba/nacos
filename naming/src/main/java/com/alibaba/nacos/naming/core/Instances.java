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

package com.alibaba.nacos.naming.core;

import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.MD5Utils;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.naming.pojo.Record;
import com.fasterxml.jackson.annotation.JsonIgnore;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Package of instance list.
 *
 * @author nkorange
 * @since 1.0.0
 */
public class Instances implements Record {
    
    private static final long serialVersionUID = 5500823673993740145L;
    
    private List<Instance> instanceList = new ArrayList<>();
    
    public List<Instance> getInstanceList() {
        return instanceList;
    }
    
    public void setInstanceList(List<Instance> instanceList) {
        this.instanceList = instanceList;
    }
    
    @Override
    public String toString() {
        try {
            return JacksonUtils.toJson(this);
        } catch (Exception e) {
            throw new RuntimeException("Instances toJSON failed", e);
        }
    }
    
    @Override
    @JsonIgnore
    public String getChecksum() {
        
        return recalculateChecksum();
    }
    
    private String recalculateChecksum() {
        StringBuilder sb = new StringBuilder();
        Collections.sort(instanceList);
        for (Instance ip : instanceList) {
            String string =
                    ip.getIp() + ":" + ip.getPort() + "_" + ip.getWeight() + "_" + ip.isHealthy() + "_" + ip.isEnabled()
                            + "_" + ip.getClusterName() + "_" + convertMap2String(ip.getMetadata());
            sb.append(string);
            sb.append(",");
        }
        
        return MD5Utils.md5Hex(sb.toString(), Constants.ENCODE);
    }
    
    /**
     * Convert Map to KV string with ':'.
     *
     * @param map map need to be converted
     * @return KV string with ':'
     */
    public String convertMap2String(Map<String, String> map) {
        
        if (map == null || map.isEmpty()) {
            return StringUtils.EMPTY;
        }
        
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            sb.append(key);
            sb.append(":");
            sb.append(map.get(key));
            sb.append(",");
        }
        return sb.toString();
    }
}
