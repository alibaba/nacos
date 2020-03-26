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

import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class HttpUtils {

    public static String buildUrl(boolean isHttps, String serverAddr, String... subPaths) {
        StringBuilder sb = new StringBuilder();
        if (isHttps) {
            sb.append("https://");
        } else {
            sb.append("http://");
        }
        sb.append(serverAddr);
        for (String subPath : subPaths) {
            if (subPath.startsWith("/")) {
                sb.append(subPath);
            } else {
                sb.append("/").append(subPath);
            }
        }
        return sb.toString();
    }

    public static Map<String, String> translateParameterMap(Map<String, String[]> parameterMap) {

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


}
