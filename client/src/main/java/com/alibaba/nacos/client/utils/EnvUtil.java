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

import com.alibaba.nacos.common.http.param.Header;
import org.slf4j.Logger;

/**
 * env util.
 *
 * @author Nacos
 */
public class EnvUtil {
    
    public static final Logger LOGGER = LogUtils.logger(EnvUtil.class);
    
    public static void setSelfEnv(Header headers) {
        if (headers != null) {
            String amorayTagTmp = headers.getValue(AMORY_TAG);
            if (amorayTagTmp == null) {
                if (selfAmorayTag != null) {
                    selfAmorayTag = null;
                    LOGGER.warn("selfAmoryTag:null");
                }
            } else {
                if (!amorayTagTmp.equals(selfAmorayTag)) {
                    selfAmorayTag = amorayTagTmp;
                    LOGGER.warn("selfAmoryTag:{}", selfAmorayTag);
                }
            }
            
            String vipserverTagTmp = headers.getValue(VIPSERVER_TAG);
            if (vipserverTagTmp == null) {
                if (selfVipserverTag != null) {
                    selfVipserverTag = null;
                    LOGGER.warn("selfVipserverTag:null");
                }
            } else {
                if (!vipserverTagTmp.equals(selfVipserverTag)) {
                    selfVipserverTag = vipserverTagTmp;
                    LOGGER.warn("selfVipserverTag:{}", selfVipserverTag);
                }
            }
            String locationTagTmp = headers.getValue(LOCATION_TAG);
            if (locationTagTmp == null) {
                if (selfLocationTag != null) {
                    selfLocationTag = null;
                    LOGGER.warn("selfLocationTag:null");
                }
            } else {
                if (!locationTagTmp.equals(selfLocationTag)) {
                    selfLocationTag = locationTagTmp;
                    LOGGER.warn("selfLocationTag:{}", selfLocationTag);
                }
            }
        }
    }
    
    public static String getSelfAmorayTag() {
        return selfAmorayTag;
    }
    
    public static String getSelfVipserverTag() {
        return selfVipserverTag;
    }
    
    public static String getSelfLocationTag() {
        return selfLocationTag;
    }
    
    private static String selfAmorayTag;
    
    private static String selfVipserverTag;
    
    private static String selfLocationTag;
    
    private static final String AMORY_TAG = "Amory-Tag";
    
    private static final String VIPSERVER_TAG = "Vipserver-Tag";
    
    private static final String LOCATION_TAG = "Location-Tag";
}
