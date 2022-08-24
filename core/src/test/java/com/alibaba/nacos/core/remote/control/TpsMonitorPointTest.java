/*
 * Copyright 1999-2021 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.remote.control;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * {@link TpsMonitorPoint} unit tests.
 *
 * @author chenglu
 * @date 2021-06-18 14:02
 */
public class TpsMonitorPointTest {
    
    @Test
    public void testStaticMethod() {
        //2022-08-23 14:23:53
        assertEquals(1661235833000L, TpsMonitorPoint.getTrimMillsOfSecond(1661235833111L));
        //2022-08-23 14:23:00
        assertEquals(1661235780000L, TpsMonitorPoint.getTrimMillsOfMinute(1661235833111L));
        //2022-08-23 14:00:00
        assertEquals(1661234400000L, TpsMonitorPoint.getTrimMillsOfHour(1661235833111L));
        
        assertEquals(LocalDateTime.of(2022, 8, 23, 14, 23, 53).atZone(ZoneOffset.ofHours(8))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
                        .withLocale(Locale.getDefault())), TpsMonitorPoint.getTimeFormatOfSecond(1661235833111L));
    }
    
}
