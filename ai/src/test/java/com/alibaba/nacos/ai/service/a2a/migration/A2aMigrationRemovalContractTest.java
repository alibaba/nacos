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

package com.alibaba.nacos.ai.service.a2a.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class A2aMigrationRemovalContractTest {
    
    private static final String REMOVAL_DECLARATION =
        "TODO(remove in 4.0): remove after the historical A2A migration window closes.";
    
    private static final String INTEGRATION_DECLARATION =
        "TODO(remove in 4.0): Temporary migration path for Nacos 3.0-3.2 A2A data.";
    
    @Test
    void everyMigrationClassAndIntegrationPointShouldBeInventoried() throws IOException {
        Path moduleRoot = moduleRoot();
        Path repositoryRoot = moduleRoot.getParent();
        Path packageRoot = moduleRoot.resolve(
            "src/main/java/com/alibaba/nacos/ai/service/a2a/migration");
        String inventory = read(repositoryRoot.resolve(
            "doc/agent-management-rad-a2a-migration-removal-inventory.md"));
        List<Path> productionClasses;
        try (Stream<Path> paths = Files.list(packageRoot)) {
            productionClasses = paths.filter(path -> path.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
        assertTrue(!productionClasses.isEmpty(), "Migration package must not be empty");
        for (Path productionClass : productionClasses) {
            String source = read(productionClass);
            assertTrue(source.contains(REMOVAL_DECLARATION),
                productionClass + " must declare the 4.0 removal lifecycle");
            assertTrue(inventory.contains(productionClass.getFileName().toString()),
                productionClass + " must appear in the removal inventory");
        }
        assertIntegrationPoint(repositoryRoot, inventory,
            "ai/src/main/java/com/alibaba/nacos/ai/service/a2a/"
                + "A2aCompatibilityModeResolver.java");
        assertIntegrationPoint(repositoryRoot, inventory,
            "ai/src/main/java/com/alibaba/nacos/ai/service/a2a/"
                + "A2aCompatibilityOperationService.java");
        assertIntegrationPoint(repositoryRoot, inventory,
            "ai/src/main/java/com/alibaba/nacos/ai/service/agent/AgentOperationService.java");
        assertIntegrationPoint(repositoryRoot, inventory,
            "core/src/main/java/com/alibaba/nacos/core/cluster/MemberMetaDataConstants.java");
    }
    
    private void assertIntegrationPoint(Path repositoryRoot, String inventory,
        String relativePath) throws IOException {
        Path sourcePath = repositoryRoot.resolve(relativePath);
        assertTrue(read(sourcePath).contains(INTEGRATION_DECLARATION),
            relativePath + " must carry the main-flow removal marker");
        assertTrue(inventory.contains(sourcePath.getFileName().toString()),
            relativePath + " must appear in the removal inventory");
    }
    
    private Path moduleRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        return Files.isDirectory(current.resolve("src/main/java")) ? current
            : current.resolve("ai");
    }
    
    private String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
