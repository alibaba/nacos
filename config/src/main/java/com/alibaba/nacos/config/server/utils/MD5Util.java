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

package com.alibaba.nacos.config.server.utils;

import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.service.ConfigCacheService;
import com.alibaba.nacos.core.utils.StringPool;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.config.server.constant.Constants.LINE_SEPARATOR;
import static com.alibaba.nacos.config.server.constant.Constants.WORD_SEPARATOR;

/**
 * MD5 util.
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class MD5Util {
    
    /**
     * Compare Md5.
     */
    public static List<String> compareMd5(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map) {
        List<String> changedGroupKeys = new ArrayList<String>();
        String tag = request.getHeader("Vipserver-Tag");
        for (Map.Entry<String, String> entry : clientMd5Map.entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            String ip = RequestUtil.getRemoteIp(request);
            boolean isUptodate = ConfigCacheService.isUptodate(groupKey, clientMd5, ip, tag);
            if (!isUptodate) {
                changedGroupKeys.add(groupKey);
            }
        }
        return changedGroupKeys;
    }
    
    /**
     * Compare old Md5.
     */
    public static String compareMd5OldResult(List<String> changedGroupKeys) {
        StringBuilder sb = new StringBuilder();
        for (String groupKey : changedGroupKeys) {
            String[] dataIdGroupId = GroupKey2.parseKey(groupKey);
            sb.append(dataIdGroupId[0]);
            sb.append(":");
            sb.append(dataIdGroupId[1]);
            sb.append(";");
        }
        return sb.toString();
    }
    
    /**
     * Join and encode changedGroupKeys string.
     */
    public static String compareMd5ResultString(List<String> changedGroupKeys) throws IOException {
        if (null == changedGroupKeys) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        
        for (String groupKey : changedGroupKeys) {
            String[] dataIdGroupId = GroupKey2.parseKey(groupKey);
            sb.append(dataIdGroupId[0]);
            sb.append(WORD_SEPARATOR);
            sb.append(dataIdGroupId[1]);
            // if have tenant, then set it
            if (dataIdGroupId.length == 3) {
                if (StringUtils.isNotBlank(dataIdGroupId[2])) {
                    sb.append(WORD_SEPARATOR);
                    sb.append(dataIdGroupId[2]);
                }
            }
            sb.append(LINE_SEPARATOR);
        }
        
        // To encode WORD_SEPARATOR and LINE_SEPARATOR invisible characters, encoded value is %02 and %01
        return URLEncoder.encode(sb.toString(), "UTF-8");
    }
    
    /**
     * Parse the transport protocol, which has two formats (W for field delimiter, L for each data delimiter) old: D w G
     * w MD5 l new: D w G w MD5 w T l.
     *
     * @param configKeysString protocol
     * @return protocol message
     */
    public static Map<String, String> getClientMd5Map(String configKeysString) {
        
        Map<String, String> md5Map = new HashMap<String, String>(5);
        
        if (null == configKeysString || "".equals(configKeysString)) {
            return md5Map;
        }
        int start = 0;
        List<String> tmpList = new ArrayList<String>(3);
        for (int i = start; i < configKeysString.length(); i++) {
            char c = configKeysString.charAt(i);
            if (c == WORD_SEPARATOR_CHAR) {
                tmpList.add(configKeysString.substring(start, i));
                start = i + 1;
                if (tmpList.size() > 3) {
                    // Malformed message and return parameter error.
                    throw new IllegalArgumentException("invalid protocol,too much key");
                }
            } else if (c == LINE_SEPARATOR_CHAR) {
                String endValue = "";
                if (start + 1 <= i) {
                    endValue = configKeysString.substring(start, i);
                }
                start = i + 1;
                
                // If it is the old message, the last digit is MD5. The post-multi-tenant message is tenant
                if (tmpList.size() == 2) {
                    String groupKey = GroupKey2.getKey(tmpList.get(0), tmpList.get(1));
                    groupKey = StringPool.get(groupKey);
                    md5Map.put(groupKey, endValue);
                } else {
                    String groupKey = GroupKey2.getKey(tmpList.get(0), tmpList.get(1), endValue);
                    groupKey = StringPool.get(groupKey);
                    md5Map.put(groupKey, tmpList.get(2));
                }
                tmpList.clear();
                
                // Protect malformed messages
                if (md5Map.size() > 10000) {
                    throw new IllegalArgumentException("invalid protocol, too much listener");
                }
            }
        }
        return md5Map;
    }
    
    public static String toString(InputStream input, String encoding) throws IOException {
        return (null == encoding) ? toString(new InputStreamReader(input, Constants.ENCODE))
                : toString(new InputStreamReader(input, encoding));
    }
    
    /**
     * Reader to String.
     */
    public static String toString(Reader reader) throws IOException {
        CharArrayWriter sw = new CharArrayWriter();
        copy(reader, sw);
        return sw.toString();
    }
    
    /**
     * Copy data to buffer.
     */
    public static long copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[1024];
        long count = 0;
        for (int n = 0; (n = input.read(buffer)) >= 0; ) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
    
    static final char WORD_SEPARATOR_CHAR = (char) 2;
    
    static final char LINE_SEPARATOR_CHAR = (char) 1;
    
}

