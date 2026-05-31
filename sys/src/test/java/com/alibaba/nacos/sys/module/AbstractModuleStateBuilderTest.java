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

package com.alibaba.nacos.sys.module;

import com.alibaba.nacos.sys.env.DeploymentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractModuleStateBuilderTest {
    
    @Test
    void testConsoleModuleStateBuilderMatchMerged() {
        AbstractConsoleModuleStateBuilder builder = new TestConsoleBuilder();
        assertTrue(builder.isMatchDeployment(DeploymentType.MERGED));
    }
    
    @Test
    void testConsoleModuleStateBuilderMatchConsole() {
        AbstractConsoleModuleStateBuilder builder = new TestConsoleBuilder();
        assertTrue(builder.isMatchDeployment(DeploymentType.CONSOLE));
    }
    
    @Test
    void testConsoleModuleStateBuilderNotMatchServer() {
        AbstractConsoleModuleStateBuilder builder = new TestConsoleBuilder();
        assertFalse(builder.isMatchDeployment(DeploymentType.SERVER));
    }
    
    @Test
    void testConsoleModuleStateBuilderNotMatchOther() {
        AbstractConsoleModuleStateBuilder builder = new TestConsoleBuilder();
        assertFalse(builder.isMatchDeployment(DeploymentType.SERVER_WITH_MCP));
        assertFalse(builder.isMatchDeployment(DeploymentType.ILLEGAL));
    }
    
    @Test
    void testServerModuleStateBuilderMatchMerged() {
        AbstractServerModuleStateBuilder builder = new TestServerBuilder();
        assertTrue(builder.isMatchDeployment(DeploymentType.MERGED));
    }
    
    @Test
    void testServerModuleStateBuilderMatchServer() {
        AbstractServerModuleStateBuilder builder = new TestServerBuilder();
        assertTrue(builder.isMatchDeployment(DeploymentType.SERVER));
    }
    
    @Test
    void testServerModuleStateBuilderNotMatchConsole() {
        AbstractServerModuleStateBuilder builder = new TestServerBuilder();
        assertFalse(builder.isMatchDeployment(DeploymentType.CONSOLE));
    }
    
    @Test
    void testServerModuleStateBuilderNotMatchOther() {
        AbstractServerModuleStateBuilder builder = new TestServerBuilder();
        assertFalse(builder.isMatchDeployment(DeploymentType.SERVER_WITH_MCP));
        assertFalse(builder.isMatchDeployment(DeploymentType.ILLEGAL));
    }
    
    private static class TestConsoleBuilder extends AbstractConsoleModuleStateBuilder {
        
        @Override
        public ModuleState build() {
            return new ModuleState("test-console");
        }
        
        public String getModuleName() {
            return "test-console";
        }
    }
    
    private static class TestServerBuilder extends AbstractServerModuleStateBuilder {
        
        @Override
        public ModuleState build() {
            return new ModuleState("test-server");
        }
        
        public String getModuleName() {
            return "test-server";
        }
    }
}
