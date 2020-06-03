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
package com.alibaba.nacos.common.utils;

import java.io.InputStream;
import java.util.Properties;

/**
 * @author xingxuechao
 * on:2019/2/27 12:32 PM
 */
public class VersionUtils {

    public static String VERSION;
    /**
     * 获取当前version
     */
    public static final String VERSION_PLACEHOLDER = "${project.version}";


    static {
        InputStream in = null;
        try {
            in = VersionUtils.class.getClassLoader()
                .getResourceAsStream("nacos-version.txt");
            Properties props = new Properties();
            props.load(in);
            String val = props.getProperty("version");
            if (val != null && !VERSION_PLACEHOLDER.equals(val)) {
                VERSION = val;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
