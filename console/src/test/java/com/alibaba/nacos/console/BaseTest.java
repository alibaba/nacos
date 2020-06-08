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
package com.alibaba.nacos.console;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

/**
 * @author zhangshun
 * @version $Id: BaseTest.java,v 0.1 2020年06月04日 11:22 $Exp
 */
public  class BaseTest {


    public static String readClassPath(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try {
            return IOUtils.toString(resource.getInputStream(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


}
