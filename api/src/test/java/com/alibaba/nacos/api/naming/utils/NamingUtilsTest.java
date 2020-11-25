package com.alibaba.nacos.api.naming.utils;

import com.alibaba.nacos.api.utils.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author mark4z
 * @date 2020/11/25 13:35
 */
public class NamingUtilsTest{
    
    @Test
    public void testGetGroupedNameOptional() {
        String onlyGroupName = NamingUtils.getGroupedNameOptional(StringUtils.EMPTY, "groupA");
        assertEquals(onlyGroupName, "groupA@@");
        
        String onlyServiceName =  NamingUtils.getGroupedNameOptional("serviceA" ,StringUtils.EMPTY);
        assertEquals(onlyServiceName, "@@serviceA");
        
        String GroupNameAndServiceName = NamingUtils.getGroupedNameOptional("serviceA" ,"groupA");
        assertEquals(GroupNameAndServiceName, "groupA@@serviceA");
    }
}