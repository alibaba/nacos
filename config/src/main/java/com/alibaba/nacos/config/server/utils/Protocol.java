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

/**
 * Used to handle protocol-related operations.
 *
 * @author zhidao
 * @version 1.0 2011/05/03
 */
public class Protocol {
    
    /**
     * fix the version number like 2.0.4(fix the version template like major.minor.bug-fix)
     *
     * @param version version
     * @return version.
     */
    public static int getVersionNumber(String version) {
        if (version == null) {
            return -1;
        }
        String[] vs = version.split("\\.");
        int sum = 0;
        for (int i = 0; i < vs.length; i++) {
            try {
                sum = sum * 10 + Integer.parseInt(vs[i]);
            } catch (Exception e) {
                // ignore
            }
        }
        return sum;
    }
}
