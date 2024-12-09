/*
 * Copyright 1999-2024 Alibaba Group Holding Ltd.
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

import com.alibaba.nacos.config.server.service.ConfigCacheService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.nacos.api.common.Constants.VIPSERVER_TAG;

/**
 * The type Nacos md5 comparator.
 *
 * @author Sunrisea
 */
public class NacosMd5Comparator implements Md5Comparator {
    
    @Override
    public String getName() {
        return "nacos";
    }
    
    @Override
    public List<String> compareMd5(HttpServletRequest request, HttpServletResponse response,
            Map<String, String> clientMd5Map) {
        List<String> changedGroupKeys = new ArrayList<>();
        String tag = request.getHeader(VIPSERVER_TAG);
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
}
