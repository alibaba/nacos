package com.alibaba.nacos.common.utils;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * DateFormatUtils test.
 * @author zzq
 */
public class DateFormatUtilsTest {
    
    @Test
    public void testformat() {
        final Calendar c = Calendar.getInstance(TimeZone.getDefault());
        c.set(2021, Calendar.JANUARY, 1, 12, 0, 0);
        c.setTimeZone(TimeZone.getDefault());
        final StringBuilder buffer = new StringBuilder ();
        final int year = c.get(Calendar.YEAR);
        final int month = c.get(Calendar.MONTH) + 1;
        final int day = c.get(Calendar.DAY_OF_MONTH);
        final int hour = c.get(Calendar.HOUR_OF_DAY);
        buffer.append (year);
        buffer.append(month);
        buffer.append(day);
        buffer.append(hour);
        Assert.assertEquals(buffer.toString(), DateFormatUtils.format(c.getTime(), "yyyyMdH"));
    }
}
