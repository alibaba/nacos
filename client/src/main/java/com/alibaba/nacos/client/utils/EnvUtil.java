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

package com.alibaba.nacos.client.utils;

import com.alibaba.nacos.api.common.Constants;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * env util.
 *
 * @author Nacos
 */
public class EnvUtil {
    
    public static final Logger LOGGER = LogUtils.logger(EnvUtil.class);
    
    private static String selfAmoryTag;
    
    private static String selfVipserverTag;
    
    private static String selfLocationTag;
    
    public static void setSelfEnv(Map<String, List<String>> headers) {
        if (headers != null) {
            List<String> amoryTagTmp = headers.get(Constants.AMORY_TAG);
            if (amoryTagTmp == null) {
                if (selfAmoryTag != null) {
                    selfAmoryTag = null;
                    LOGGER.warn("selfAmoryTag:null");
                }
            } else {
                String amoryTagTmpStr = listToString(amoryTagTmp);
                if (!Objects.equals(amoryTagTmpStr, selfAmoryTag)) {
                    selfAmoryTag = amoryTagTmpStr;
                    LOGGER.warn("selfAmoryTag:{}", selfAmoryTag);
                }
            }
            
            List<String> vipserverTagTmp = headers.get(Constants.VIPSERVER_TAG);
            if (vipserverTagTmp == null) {
                if (selfVipserverTag != null) {
                    selfVipserverTag = null;
                    LOGGER.warn("selfVipserverTag:null");
                }
            } else {
                String vipserverTagTmpStr = listToString(vipserverTagTmp);
                if (!Objects.equals(vipserverTagTmpStr, selfVipserverTag)) {
                    selfVipserverTag = vipserverTagTmpStr;
                    LOGGER.warn("selfVipserverTag:{}", selfVipserverTag);
                }
            }
            List<String> locationTagTmp = headers.get(Constants.LOCATION_TAG);
            if (locationTagTmp == null) {
                if (selfLocationTag != null) {
                    selfLocationTag = null;
                    LOGGER.warn("selfLocationTag:null");
                }
            } else {
                String locationTagTmpStr = listToString(locationTagTmp);
                if (!Objects.equals(locationTagTmpStr, selfLocationTag)) {
                    selfLocationTag = locationTagTmpStr;
                    LOGGER.warn("selfLocationTag:{}", selfLocationTag);
                }
            }
        }
    }
    
    public static String getSelfAmoryTag() {
        return selfAmoryTag;
    }
    
    public static String getSelfVipserverTag() {
        return selfVipserverTag;
    }
    
    public static String getSelfLocationTag() {
        return selfLocationTag;
    }
    
    private static String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        for (String string : list) {
            result.append(string);
            result.append(',');
        }
        return result.substring(0, result.length() - 1);
    }
}