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

package com.alibaba.nacos.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Ensures that the bootstrap configuration follows the distribution configuration.
 */
class ApplicationPropertiesConsistencyTest {
    
    @Test
    void shouldFollowDistributionApplicationProperties() throws IOException {
        // Given
        Path repositoryRoot = findRepositoryRoot(Path.of(System.getProperty("user.dir")));
        Path distributionProperties =
            repositoryRoot.resolve("distribution/conf/application.properties");
        Path bootstrapProperties =
            repositoryRoot.resolve("bootstrap/src/main/resources/application.properties");
        
        // When
        String expected = readUtf8(distributionProperties);
        String actual = readUtf8(bootstrapProperties);
        
        // Then
        assertEquals(expected, actual,
            "Bootstrap application.properties must follow distribution/conf");
    }
    
    @Test
    void shouldRejectPathOutsideRepository() {
        // Given
        Path unrelatedPath =
            Path.of(System.getProperty("java.io.tmpdir"), "missing-nacos-repository");
        
        // When & Then
        assertThrows(IllegalStateException.class, () -> findRepositoryRoot(unrelatedPath));
    }
    
    /**
     * Finds the repository root from a path inside the working tree.
     *
     * @param start path used to start the lookup
     * @return repository root containing both application.properties files
     * @throws IllegalStateException when the repository root cannot be found
     */
    private static Path findRepositoryRoot(Path start) {
        Path current = start.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                && Files.isRegularFile(current.resolve("distribution/conf/application.properties"))
                && Files.isRegularFile(
                    current.resolve("bootstrap/src/main/resources/application.properties"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate the Nacos repository root from " + start);
    }
    
    private static String readUtf8(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
