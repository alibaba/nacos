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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.nacos.naming.misc.Loggers;
import com.alibaba.nacos.naming.pojo.Record;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Package of instance list
 *
 * @author nkorange
 * @since 1.0.0
 */
public class Instances implements Record {

    private String cachedChecksum;

    private long lastCalculateTime = 0L;

    private List<Instance> instanceList = new ArrayList<>();

    public List<Instance> getInstanceList() {
        return instanceList;
    }

    public void setInstanceList(List<Instance> instanceList) {
        this.instanceList = instanceList;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }

    @Override
    @JSONField(serialize = false)
    public String getChecksum() {
        recalculateChecksum();
        return cachedChecksum;
    }

    public String getCachedChecksum() {
        return cachedChecksum;
    }

    private void recalculateChecksum() {
        StringBuilder sb = new StringBuilder();
        Collections.sort(instanceList);
        for (Instance ip : instanceList) {
            String string = ip.getIp() + ":" + ip.getPort() + "_" + ip.getWeight() + "_"
                + ip.isHealthy() + "_" + ip.isEnabled() + "_" + ip.getClusterName() + "_" + convertMap2String(ip.getMetadata());
            sb.append(string);
            sb.append(",");
        }
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            cachedChecksum =
                new BigInteger(1, md5.digest((sb.toString()).getBytes(Charset.forName("UTF-8")))).toString(16);
        } catch (NoSuchAlgorithmException e) {
            Loggers.SRV_LOG.error("error while calculating checksum(md5) for instances", e);
            cachedChecksum = RandomStringUtils.randomAscii(32);
        }
        lastCalculateTime = System.currentTimeMillis();
    }

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
