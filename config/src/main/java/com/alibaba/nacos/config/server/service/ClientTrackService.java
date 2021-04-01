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
 * ClientTrackService which tracks client's md5 service and delete expired ip's records.
 *
 * @author Nacos
 */
public class ClientTrackService {
    
    /**
     * Track client's md5 value.
     */
    public static void trackClientMd5(String ip, Map<String, String> clientMd5Map) {
        ClientRecord record = getClientRecord(ip);
        record.setLastTime(System.currentTimeMillis());
        record.getGroupKey2md5Map().putAll(clientMd5Map);
    }
    
    /**
     * TrackClientMd5.
     *
     * @param ip                     ip string value.
     * @param clientMd5Map           clientMd5Map.
     * @param clientLastPollingTsMap clientLastPollingTsMap.
     */
    public static void trackClientMd5(String ip, Map<String, String> clientMd5Map,
            Map<String, Long> clientLastPollingTsMap) {
        ClientRecord record = getClientRecord(ip);
        record.setLastTime(System.currentTimeMillis());
        record.getGroupKey2md5Map().putAll(clientMd5Map);
        record.getGroupKey2pollingTsMap().putAll(clientLastPollingTsMap);
    }
    
    /**
     * Put the specified value(ip/groupKey/clientMd5) into clientRecords Map.
     *
     * @param ip        ip string value.
     * @param groupKey  groupKey string value.
     * @param clientMd5 clientMd5 string value.
     */
    public static void trackClientMd5(String ip, String groupKey, String clientMd5) {
        ClientRecord record = getClientRecord(ip);
        record.setLastTime(System.currentTimeMillis());
        record.getGroupKey2md5Map().put(groupKey, clientMd5);
        record.getGroupKey2pollingTsMap().put(groupKey, record.getLastTime());
    }
    
    /**
     * Get subscribe client count.
     *
     * @return subscribe client count.
     */
    public static int subscribeClientCount() {
        return clientRecords.size();
    }
    
    /**
     * Get all of subscriber count.
     *
     * @return all of subscriber count.
     */
    public static long subscriberCount() {
        long count = 0;
        for (ClientRecord record : clientRecords.values()) {
            count += record.getGroupKey2md5Map().size();
        }
        return count;
    }
    
    /**
     * Groupkey ->  SubscriberStatus.
     */
    public static Map<String, SubscriberStatus> listSubStatus(String ip) {
        Map<String, SubscriberStatus> status = new HashMap<String, SubscriberStatus>(100);
        
        ClientRecord record = getClientRecord(ip);
        if (record == null) {
            return status;
        }
        
        for (Map.Entry<String, String> entry : record.getGroupKey2md5Map().entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            long lastPollingTs = record.getGroupKey2pollingTsMap().get(groupKey);
            boolean isUpdate = ConfigCacheService.isUptodate(groupKey, clientMd5);
            
            status.put(groupKey, new SubscriberStatus(groupKey, isUpdate, clientMd5, lastPollingTs));
        }
        
        return status;
    }
    
    /**
     * Ip ->  SubscriberStatus.
     */
    public static Map<String, SubscriberStatus> listSubsByGroup(String groupKey) {
        Map<String, SubscriberStatus> subs = new HashMap<String, SubscriberStatus>(100);
        
        for (ClientRecord clientRec : clientRecords.values()) {
            String clientMd5 = clientRec.getGroupKey2md5Map().get(groupKey);
            Long lastPollingTs = clientRec.getGroupKey2pollingTsMap().get(groupKey);
            
            if (null != clientMd5 && null != lastPollingTs) {
                Boolean isUpdate = ConfigCacheService.isUptodate(groupKey, clientMd5);
                subs.put(clientRec.getIp(), new SubscriberStatus(groupKey, isUpdate, clientMd5, lastPollingTs));
            }
            
        }
        return subs;
    }
    
    /**
     * Specify subscriber's ip and look up whether data is latest.
     * groupKey -> isUptodate.
     */
    public static Map<String, Boolean> isClientUptodate(String ip) {
        Map<String, Boolean> result = new HashMap<String, Boolean>(100);
        for (Map.Entry<String, String> entry : getClientRecord(ip).getGroupKey2md5Map().entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            Boolean isuptodate = ConfigCacheService.isUptodate(groupKey, clientMd5);
            result.put(groupKey, isuptodate);
        }
        return result;
    }
    
    /**
     * Specify groupKey and look up whether subscriber and data is latest.
     * IP -> isUptodate.
     */
    public static Map<String, Boolean> listSubscriberByGroup(String groupKey) {
        Map<String, Boolean> subs = new HashMap<String, Boolean>(100);
        
        for (ClientRecord clientRec : clientRecords.values()) {
            String clientMd5 = clientRec.getGroupKey2md5Map().get(groupKey);
            if (null != clientMd5) {
                Boolean isuptodate = ConfigCacheService.isUptodate(groupKey, clientMd5);
                subs.put(clientRec.getIp(), isuptodate);
            }
        }
        return subs;
    }
    
    /**
     * Get and return the record of specified client ip.
     *
     * @param clientIp clientIp string value.
     * @return the record of specified client ip.
     */
    private static ClientRecord getClientRecord(String clientIp) {
        ClientRecord record = clientRecords.get(clientIp);
        if (null != record) {
            return record;
        }
        ClientRecord clientRecord = new ClientRecord(clientIp);
        clientRecords.putIfAbsent(clientIp, clientRecord);
        return clientRecord;
    }
    
    public static void refreshClientRecord() {
        clientRecords = new ConcurrentHashMap<String, ClientRecord>(50);
    }
    
    /**
     * All of client records, adding or deleting.
     */
    static volatile ConcurrentMap<String, ClientRecord> clientRecords = new ConcurrentHashMap<String, ClientRecord>();
}


