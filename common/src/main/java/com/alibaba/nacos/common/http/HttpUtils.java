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

package com.alibaba.nacos.common.http;

import com.alibaba.nacos.common.utils.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class HttpUtils {

    private static final Pattern CONTEXT_PATH_MATCH = Pattern.compile("(\\/)\\1+");

    public static String buildUrl(boolean isHttps, String serverAddr, String... subPaths) {
        StringBuilder sb = new StringBuilder();
        if (isHttps) {
            sb.append("https://");
        } else {
            sb.append("http://");
        }
        sb.append(serverAddr);
        String pre = null;
        for (String subPath : subPaths) {
            if (StringUtils.isBlank(subPath)) {
                continue;
            }
            Matcher matcher = CONTEXT_PATH_MATCH.matcher(subPath);
            if (matcher.find()) {
                throw new IllegalArgumentException("Illegal url path expression : " + subPath);
            }
            if (pre == null || !pre.endsWith("/")) {
                if (subPath.startsWith("/")) {
                    sb.append(subPath);
                }
                else {
                    sb.append("/").append(subPath);
                }
            } else {
                if (subPath.startsWith("/")) {
                    sb.append(subPath.replaceFirst("\\/", ""));
                }
                else {
                    sb.append(subPath);
                }
            }
            pre = subPath;
        }
        return sb.toString();
    }

    public static Map<String, String> translateParameterMap(Map<String, String[]> parameterMap) throws Exception {
        Map<String, String> map = new HashMap<String, String>(16);
        for (String key : parameterMap.keySet()) {
            map.put(key, parameterMap.get(key)[0]);
        }
        return map;
    }

    public static String encodingParams(Map<String, String> params, String encoding)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (null == params || params.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (StringUtils.isEmpty(entry.getValue())) {
                continue;
            }

            sb.append(entry.getKey()).append("=");
            sb.append(URLEncoder.encode(entry.getValue(), encoding));
            sb.append("&");
        }

        return sb.toString();
    }

    public static String encodingParams(List<String> paramValues, String encoding)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (null == paramValues) {
            return null;
        }

        for (Iterator<String> iter = paramValues.iterator(); iter.hasNext(); ) {
            sb.append(iter.next()).append("=");
            sb.append(URLEncoder.encode(iter.next(), encoding));
            if (iter.hasNext()) {
                sb.append("&");
            }
        }
        return sb.toString();
    }

    public static String decode(String str, String encode) throws UnsupportedEncodingException {
        return innerDecode(null, str, encode);
    }

    private static String innerDecode(String pre, String now, String encode) throws UnsupportedEncodingException {
        // Because the data may be encoded by the URL more than once,
        // it needs to be decoded recursively until it is fully successful
        if (StringUtils.equals(pre, now)) {
            return pre;
        }
        pre = now;
        now = URLDecoder.decode(now, encode);
        return innerDecode(pre, now, encode);
    }


}
