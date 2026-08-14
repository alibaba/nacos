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

package com.alibaba.nacos.naming.healthcheck.v2.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthCheckAddressValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.1", "mysql-service.internal",
            "mysql_service", "[::1]", "::1", "fe80::1%eth0"})
    void testValidAddress(String address) {
        assertTrue(HealthCheckAddressValidator.isValid(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "host:3306", "host/path", "host?query=true", "host#fragment",
            "user@host", "host\\path", "host%3Fquery=true", "[::1]:3306", "[::1]suffix",
            "rogue-mysql:3306?allowLoadLocalInfile=true#", "host\nquery=true"})
    void testInvalidAddress(String address) {
        assertFalse(HealthCheckAddressValidator.isValid(address));
    }

    @Test
    void testNullAddress() {
        assertFalse(HealthCheckAddressValidator.isValid(null));
    }
}
