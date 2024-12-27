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

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DateFormatUtils test.
 *
 * @author zzq
 */
class DateFormatUtilsTest {
    
    @Test
    void testformat() {
        final Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.set(2021, Calendar.JANUARY, 1, 12, 0, 0);
        c.setTimeZone(TimeZone.getDefault());
        final StringBuilder buffer = new StringBuilder();
        final int year = c.get(Calendar.YEAR);
        final int month = c.get(Calendar.MONTH) + 1;
        final int day = c.get(Calendar.DAY_OF_MONTH);
        final int hour = c.get(Calendar.HOUR_OF_DAY);
        buffer.append(year);
        buffer.append(month);
        buffer.append(day);
        buffer.append(hour);
        assertEquals(buffer.toString(), DateFormatUtils.format(c.getTime(), "yyyyMdH"));
    }
    
    @Test
    void testForNullPointerExceptionWithDate() {
        assertThrows(NullPointerException.class, () -> {
            DateFormatUtils.format(new Date(), null);
        });
    }
    
    @Test
    void testForNullPointerExceptionWithPattern() {
        assertThrows(NullPointerException.class, () -> {
            DateFormatUtils.format(null, "yyyyMdH");
        });
    }
}
