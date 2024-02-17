package com.alibaba.nacos.api.model.v2;

import static org.junit.Assert.assertEquals;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

public class ErrorCodeTest {
	@Test
    public void testCodeNotSame() {
        Class<ErrorCode> errorCodeClass = ErrorCode.class;
        
        ErrorCode[] errorCodes = errorCodeClass.getEnumConstants();
        Set<Integer> codeSet = new HashSet<Integer>(errorCodes.length);
        
        for(ErrorCode errorCode : errorCodes) {
        	codeSet.add(errorCode.getCode());
        }
        
        assertEquals(errorCodes.length, codeSet.size());
    }
}
