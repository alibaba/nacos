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

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TimeUtilsTest {
    
    @Test
    void testGetCurrentTimeStr() throws ParseException {
        
        Date date1 = new Date(TimeUtils.getCurrentTime().getTime());
        assertNotNull(date1.toString());
        
        Date date2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(TimeUtils.getCurrentTimeStr());
        assertNotNull(date2.toString());
        
    }
}
