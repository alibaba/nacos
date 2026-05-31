/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.naming.selector.interpreter;

import com.alibaba.nacos.api.exception.NacosException;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionInterpreterTest {
    
    @Test
    void testNewInstance() {
        ExpressionInterpreter expressionInterpreter = new ExpressionInterpreter();
        
        assertEquals(ExpressionInterpreter.class, expressionInterpreter.getClass());
    }
    
    @Test
    void testParseExpressionWhenBlank() throws NacosException {
        Set<String> actual = ExpressionInterpreter.parseExpression(" ");
        
        assertTrue(actual.isEmpty());
    }
    
    @Test
    void testParseExpressionWithMultipleLabels() throws NacosException {
        Set<String> actual = ExpressionInterpreter.parseExpression(
            "CONSUMER.label.zone = PROVIDER.label.zone & CONSUMER.label.version = PROVIDER.label.version");
        
        assertEquals(2, actual.size());
        assertTrue(actual.contains("zone"));
        assertTrue(actual.contains("version"));
    }
    
    @Test
    void testGetTerms() {
        List<String> actual =
            ExpressionInterpreter.getTerms("CONSUMER.label.zone=PROVIDER.label.zone");
        
        assertEquals(3, actual.size());
        assertEquals("CONSUMER.label.zone", actual.get(0));
        assertEquals("=", actual.get(1));
        assertEquals("PROVIDER.label.zone", actual.get(2));
    }
    
    @Test
    void testParseExpressionThrowsWhenConsumerPrefixInvalid() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression("CONSUMER.zone=PROVIDER.label.zone"));
    }
    
    @Test
    void testParseExpressionThrowsWhenMissingConnector() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression("CONSUMER.label.zone"));
    }
    
    @Test
    void testParseExpressionThrowsWhenInnerConnectorInvalid() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression(
                "CONSUMER.label.zone&PROVIDER.label.zone"));
    }
    
    @Test
    void testParseExpressionThrowsWhenProviderPrefixInvalid() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression("CONSUMER.label.zone=PROVIDER.zone"));
    }
    
    @Test
    void testParseExpressionThrowsWhenLabelMismatch() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression(
                "CONSUMER.label.zone=PROVIDER.label.version"));
    }
    
    @Test
    void testParseExpressionThrowsWhenOuterConnectorInvalid() {
        assertThrows(NacosException.class,
            () -> ExpressionInterpreter.parseExpression(
                "CONSUMER.label.zone=PROVIDER.label.zone=CONSUMER.label.version=PROVIDER.label.version"));
    }
    
    @Test
    void testSkipEmptySkipsBlankTerms() throws Exception {
        int actual = (int) invokePrivateStatic("skipEmpty",
            new Class[] {List.class, int.class}, List.of("", "CONSUMER.label.zone"), 0);
        
        assertEquals(1, actual);
    }
    
    @Test
    void testCheckOuterSyntaxReturnsEndWhenOnlyBlankTerms() throws Exception {
        int actual = (int) invokePrivateStatic("checkOuterSyntax",
            new Class[] {List.class, int.class}, List.of(""), 0);
        
        assertEquals(1, actual);
    }
    
    @Test
    void testCheckInnerSyntaxReturnsInvalidWhenNoConsumerTerm() throws Exception {
        int actual = (int) invokePrivateStatic("checkInnerSyntax",
            new Class[] {List.class, int.class}, List.of(), 0);
        
        assertEquals(-1, actual);
    }
    
    @Test
    void testCheckInnerSyntaxReturnsInvalidWhenProviderMissing() throws Exception {
        int actual = (int) invokePrivateStatic("checkInnerSyntax",
            new Class[] {List.class, int.class}, List.of("CONSUMER.label.zone", "="), 0);
        
        assertEquals(-1, actual);
    }
    
    private Object invokePrivateStatic(String methodName, Class<?>[] parameterTypes,
        Object... args) throws Exception {
        Method method = ExpressionInterpreter.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
