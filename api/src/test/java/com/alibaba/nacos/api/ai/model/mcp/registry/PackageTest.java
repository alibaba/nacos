/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.ai.model.mcp.registry;

import com.alibaba.nacos.api.remote.request.BasicRequestTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageTest extends BasicRequestTest {
    
    @Test
    void testSerialize() throws JsonProcessingException {
        Package pkg = new Package();
        pkg.setRegistryType("maven");
        pkg.setRegistryBaseUrl("https://repo.maven.apache.org/maven2/");
        pkg.setIdentifier("com.alibaba.nacos:test-package");
        pkg.setVersion("1.0.0");
        pkg.setFileSha256("abc123");
        pkg.setRuntimeHint("java11");
        
        // Create test arguments
        NamedArgument namedArgument = new NamedArgument();
        namedArgument.setName("arg1");
        namedArgument.setValue("value1");
        
        PositionalArgument positionalArgument = new PositionalArgument();
        positionalArgument.setValueHint("posValue");
        
        pkg.setRuntimeArguments(Collections.singletonList(namedArgument));
        pkg.setPackageArguments(Arrays.asList(namedArgument, positionalArgument));
        
        KeyValueInput envVar = new KeyValueInput();
        envVar.setName("ENV_VAR");
        envVar.setValue("env_value");
        pkg.setEnvironmentVariables(Collections.singletonList(envVar));
        
        String json = mapper.writeValueAsString(pkg);
        assertNotNull(json);
        assertTrue(json.contains("\"registryType\":\"maven\""));
        assertTrue(json.contains("\"registryBaseUrl\":\"https://repo.maven.apache.org/maven2/\""));
        assertTrue(json.contains("\"identifier\":\"com.alibaba.nacos:test-package\""));
        assertTrue(json.contains("\"version\":\"1.0.0\""));
        assertTrue(json.contains("\"fileSha256\":\"abc123\""));
        assertTrue(json.contains("\"runtimeHint\":\"java11\""));
        assertTrue(json.contains("\"runtimeArguments\":["));
        assertTrue(json.contains("\"packageArguments\":["));
        assertTrue(json.contains("\"environmentVariables\":["));
    }
    
    @Test
    void testDeserialize() throws JsonProcessingException {
        String json = "{"
                + "\"registryType\":\"maven\","
                + "\"registryBaseUrl\":\"https://repo.maven.apache.org/maven2/\","
                + "\"identifier\":\"com.alibaba.nacos:test-package\","
                + "\"version\":\"1.0.0\","
                + "\"fileSha256\":\"abc123\","
                + "\"runtimeHint\":\"java11\","
                + "\"runtimeArguments\":[{\"type\":\"named\",\"name\":\"arg1\",\"value\":\"value1\"}],"
                + "\"packageArguments\":["
                + "  {\"type\":\"named\",\"name\":\"arg1\",\"value\":\"value1\"},"
                + "  {\"type\":\"positional\",\"valueHint\":\"posValue\"}"
                + "],"
                + "\"environmentVariables\":[{\"name\":\"ENV_VAR\",\"value\":\"env_value\"}]"
                + "}";
        
        Package pkg = mapper.readValue(json, Package.class);
        assertNotNull(pkg);
        assertEquals("maven", pkg.getRegistryType());
        assertEquals("https://repo.maven.apache.org/maven2/", pkg.getRegistryBaseUrl());
        assertEquals("com.alibaba.nacos:test-package", pkg.getIdentifier());
        assertEquals("1.0.0", pkg.getVersion());
        assertEquals("abc123", pkg.getFileSha256());
        assertEquals("java11", pkg.getRuntimeHint());
        assertEquals(1, pkg.getRuntimeArguments().size());
        assertEquals("named", ((NamedArgument) pkg.getRuntimeArguments().get(0)).getType());
        assertEquals(2, pkg.getPackageArguments().size());
        assertEquals("named", ((NamedArgument) pkg.getPackageArguments().get(0)).getType());
        assertEquals("positional", ((PositionalArgument) pkg.getPackageArguments().get(1)).getType());
        assertEquals(1, pkg.getEnvironmentVariables().size());
        assertEquals("ENV_VAR", pkg.getEnvironmentVariables().get(0).getName());
    }
}