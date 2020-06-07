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
 * 轮询逻辑封装类
 *
 * @author Nacos
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class MD5Util {

    static public List<String> compareMd5(HttpServletRequest request,
                                          HttpServletResponse response, Map<String, String> clientMd5Map) {
        List<String> changedGroupKeys = new ArrayList<String>();
        String tag = request.getHeader("Vipserver-Tag");
        for (Map.Entry<String, String> entry : clientMd5Map.entrySet()) {
            String groupKey = entry.getKey();
            String clientMd5 = entry.getValue();
            String ip = RequestUtil.getRemoteIp(request);
            boolean isUptodate = ConfigCacheService
					.isUptodate(groupKey, clientMd5, ip, tag);
            if (!isUptodate) {
                changedGroupKeys.add(groupKey);
            }
        }
        return changedGroupKeys;
    }

    static public String compareMd5OldResult(List<String> changedGroupKeys) {
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

    static public String compareMd5ResultString(List<String> changedGroupKeys) throws
            IOException {
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

        // 对WORD_SEPARATOR和LINE_SEPARATOR不可见字符进行编码, 编码后的值为%02和%01
        return URLEncoder.encode(sb.toString(), "UTF-8");
    }

    /**
     * 解析传输协议 传输协议有两种格式(w为字段分隔符，l为每条数据分隔符)： 老报文：D w G w MD5 l 新报文：D w G w MD5 w T l
     *
     * @param configKeysString 协议字符串
     * @return 协议报文
     */
    static public Map<String, String> getClientMd5Map(String configKeysString) {

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
                    // 畸形报文。返回参数错误
                    throw new IllegalArgumentException("invalid protocol,too much key");
                }
            } else if (c == LINE_SEPARATOR_CHAR) {
                String endValue = "";
                if (start + 1 <= i) {
                    endValue = configKeysString.substring(start, i);
                }
                start = i + 1;

                // 如果老的报文，最后一位是md5。多租户后报文为tenant。
                if (tmpList.size() == 2) {
                    String groupKey = GroupKey2.getKey(tmpList.get(0), tmpList.get(1));
                    groupKey = SingletonRepository.DataIdGroupIdCache.getSingleton(groupKey);
                    md5Map.put(groupKey, endValue);
                } else {
                    String groupKey = GroupKey2.getKey(tmpList.get(0), tmpList.get(1), endValue);
                    groupKey = SingletonRepository.DataIdGroupIdCache.getSingleton(groupKey);
                    md5Map.put(groupKey, tmpList.get(2));
                }
                tmpList.clear();

                // 对畸形报文进行保护
                if (md5Map.size() > 10000) {
                    throw new IllegalArgumentException("invalid protocol, too much listener");
                }
            }
        }
        return md5Map;
    }

    static public String toString(InputStream input, String encoding) throws IOException {
        return (null == encoding) ? toString(new InputStreamReader(input, Constants.ENCODE))
            : toString(new InputStreamReader(input, encoding));
    }

    static public String toString(Reader reader) throws IOException {
        CharArrayWriter sw = new CharArrayWriter();
        copy(reader, sw);
        return sw.toString();
    }

    static public long copy(Reader input, Writer output) throws IOException {
        char[] buffer = new char[1024];
        long count = 0;
        for (int n = 0; (n = input.read(buffer)) >= 0; ) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    static final char WORD_SEPARATOR_CHAR = (char)2;
    static final char LINE_SEPARATOR_CHAR = (char)1;

}

