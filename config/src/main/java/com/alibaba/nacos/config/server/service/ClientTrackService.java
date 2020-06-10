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
package com.alibaba.nacos.config.server.service;

import com.alibaba.nacos.config.server.model.SubscriberStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 跟踪客户端md5的服务。 一段时间没有比较md5后，就删除IP对应的记录。
 *
 * @author Nacos
 */
public class ClientTrackService {
    /**
     * 跟踪客户端md5.
     */
    static public void trackClientMd5(String ip, Map<String, String> clientMd5Map) {
        ClientRecord record = getClientRecord(ip);
        record.lastTime = System.currentTimeMillis();
        record.groupKey2md5Map.putAll(clientMd5Map);
    }

    static public void trackClientMd5(String ip, Map<String, String> clientMd5Map,
                                      Map<String, Long> clientlastPollingTSMap) {
        ClientRecord record = getClientRecord(ip);
        record.lastTime = System.currentTimeMillis();
        record.groupKey2md5Map.putAll(clientMd5Map);
        record.groupKey2pollingTsMap.putAll(clientlastPollingTSMap);
    }

    static public void trackClientMd5(String ip, String groupKey, String clientMd5) {
        ClientRecord record = getClientRecord(ip);
        record.lastTime = System.currentTimeMillis();
        record.groupKey2md5Map.put(groupKey, clientMd5);
        record.groupKey2pollingTsMap.put(groupKey, record.lastTime);
    }

    /**
     * 返回订阅者客户端个数
     */
    static public int subscribeClientCount() {
        return clientRecords.size();
    }

    /**
     * 返回所有订阅者个数
     */
    static public long subscriberCount() {
        long count = 0;
        for (ClientRecord record : clientRecords.values()) {
            count += record.groupKey2md5Map.size();
        }
        return count;
    }

    /**
     * groupkey ->  SubscriberStatus
     */
    static public Map<String, SubscriberStatus> listSubStatus(String ip) {
        Map<String, SubscriberStatus> status = new HashMap<String, SubscriberStatus>(100);

        ClientRecord record = getClientRecord(ip);
        if (record == null) {
            return status;
        }

        for (Map.Entry<String, String> entry : record.groupKey2md5Map.entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            long lastPollingTs = record.groupKey2pollingTsMap.get(groupKey);
            boolean isUpdate = ConfigCacheService.isUptodate(groupKey, clientMd5);

            status.put(groupKey, new SubscriberStatus(groupKey, isUpdate, clientMd5, lastPollingTs));
        }

        return status;
    }

    /**
     * ip ->  SubscriberStatus
     */
    static public Map<String, SubscriberStatus> listSubsByGroup(String groupKey) {
        Map<String, SubscriberStatus> subs = new HashMap<String, SubscriberStatus>(100);

        for (ClientRecord clientRec : clientRecords.values()) {
            String clientMd5 = clientRec.groupKey2md5Map.get(groupKey);
            Long lastPollingTs = clientRec.groupKey2pollingTsMap.get(groupKey);

            if (null != clientMd5 && lastPollingTs != null) {
                Boolean isUpdate = ConfigCacheService.isUptodate(groupKey, clientMd5);
                subs.put(clientRec.ip, new SubscriberStatus(groupKey, isUpdate, clientMd5, lastPollingTs));
            }

        }
        return subs;
    }

    /**
     * 指定订阅者IP，查找数据是否最新。 groupKey -> isUptodate
     */
    static public Map<String, Boolean> isClientUptodate(String ip) {
        Map<String, Boolean> result = new HashMap<String, Boolean>(100);
        for (Map.Entry<String, String> entry : getClientRecord(ip).groupKey2md5Map.entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            Boolean isuptodate = ConfigCacheService.isUptodate(groupKey, clientMd5);
            result.put(groupKey, isuptodate);
        }
        return result;
    }

    /**
     * 指定groupKey，查找所有订阅者以及数据是否最新。 IP -> isUptodate
     */
    static public Map<String, Boolean> listSubscriberByGroup(String groupKey) {
        Map<String, Boolean> subs = new HashMap<String, Boolean>(100);

        for (ClientRecord clientRec : clientRecords.values()) {
            String clientMd5 = clientRec.groupKey2md5Map.get(groupKey);
            if (null != clientMd5) {
                Boolean isuptodate = ConfigCacheService.isUptodate(groupKey, clientMd5);
                subs.put(clientRec.ip, isuptodate);
            }
        }
        return subs;
    }

    /**
     * 找到指定clientIp对应的记录。
     */
    static private ClientRecord getClientRecord(String clientIp) {
        ClientRecord record = clientRecords.get(clientIp);
        if (null != record) {
            return record;
        }
        clientRecords.putIfAbsent(clientIp, new ClientRecord(clientIp));
        return clientRecords.get(clientIp);
    }

    static public void refreshClientRecord() {
        clientRecords = new ConcurrentHashMap<String, ClientRecord>(50);
    }

    /**
     * 所有客户端记录。遍历 >> 新增/删除
     */
    static volatile ConcurrentMap<String, ClientRecord> clientRecords = new ConcurrentHashMap<String, ClientRecord>();
}

/**
 * 保存客户端拉数据的记录。
 */
class ClientRecord {
    final String ip;
    volatile long lastTime;
    final ConcurrentMap<String, String> groupKey2md5Map;
    final ConcurrentMap<String, Long> groupKey2pollingTsMap;

    ClientRecord(String clientIp) {
        ip = clientIp;
        groupKey2md5Map = new ConcurrentHashMap<String, String>(20, 0.75f, 1);
        groupKey2pollingTsMap = new ConcurrentHashMap<String, Long>(20, 0.75f, 1);
    }
}


