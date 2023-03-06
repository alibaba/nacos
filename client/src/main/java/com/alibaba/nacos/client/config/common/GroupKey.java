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

package com.alibaba.nacos.client.config.common;

import com.alibaba.nacos.common.utils.StringUtils;

/**
 * Synthesize the form of dataId+groupId. Escapes reserved characters in dataId and groupId.
 *
 * @author Nacos
 */
public class GroupKey {
    
    private static final char PLUS = '+';
    
    private static final char PERCENT = '%';
    
    private static final char TWO = '2';
    
    private static final char B = 'B';
    
    private static final char FIVE = '5';
    
    public static String getKey(String dataId, String group) {
        return getKey(dataId, group, "");
    }
    
    public static String getKey(String dataId, String group, String datumStr) {
        return doGetKey(dataId, group, datumStr);
    }
    
    public static String getKeyTenant(String dataId, String group, String tenant) {
        return doGetKey(dataId, group, tenant);
    }
    
    private static String doGetKey(String dataId, String group, String datumStr) {
        if (StringUtils.isBlank(dataId)) {
            throw new IllegalArgumentException("invalid dataId");
        }
        if (StringUtils.isBlank(group)) {
            throw new IllegalArgumentException("invalid group");
        }
        StringBuilder sb = new StringBuilder();
        urlEncode(dataId, sb);
        sb.append(PLUS);
        urlEncode(group, sb);
        if (StringUtils.isNotEmpty(datumStr)) {
            sb.append(PLUS);
            urlEncode(datumStr, sb);
        }
        
        return sb.toString();
    }
    
    /**
     * Parse key.
     *
     * @param groupKey group key
     * @return parsed key
     */
    public static String[] parseKey(String groupKey) {
        StringBuilder sb = new StringBuilder();
        String dataId = null;
        String group = null;
        String tenant = null;
        
        for (int i = 0; i < groupKey.length(); ++i) {
            char c = groupKey.charAt(i);
            if (PLUS == c) {
                if (null == dataId) {
                    dataId = sb.toString();
                    sb.setLength(0);
                } else if (null == group) {
                    group = sb.toString();
                    sb.setLength(0);
                } else {
                    throw new IllegalArgumentException("invalid groupkey:" + groupKey);
                }
            } else if (PERCENT == c) {
                char next = groupKey.charAt(++i);
                char nextnext = groupKey.charAt(++i);
                if (TWO == next && B == nextnext) {
                    sb.append(PLUS);
                } else if (TWO == next && FIVE == nextnext) {
                    sb.append(PERCENT);
                } else {
                    throw new IllegalArgumentException("invalid groupkey:" + groupKey);
                }
            } else {
                sb.append(c);
            }
        }
        
        if (group == null) {
            group = sb.toString();
        } else {
            tenant = sb.toString();
        }
        
        if (StringUtils.isBlank(dataId)) {
            throw new IllegalArgumentException("invalid dataId");
        }
        if (StringUtils.isBlank(group)) {
            throw new IllegalArgumentException("invalid group");
        }
        return new String[] {dataId, group, tenant};
    }
    
    /**
     * + -> %2B % -> %25.
     */
    static void urlEncode(String str, StringBuilder sb) {
        for (int idx = 0; idx < str.length(); ++idx) {
            char c = str.charAt(idx);
            if (PLUS == c) {
                sb.append("%2B");
            } else if (PERCENT == c) {
                sb.append("%25");
            } else {
                sb.append(c);
            }
        }
    }
    
}
