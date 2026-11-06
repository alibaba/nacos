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

package com.alibaba.nacos.test.nativeimage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The restart check of {@link NativeRestartCheck} on a Server of its own. The environment that
 * {@link NativeEnvironmentMain} keeps for the integration suites runs the same check at its end,
 * on a Server that holds the data of the suites.
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NativeServerRestartITCase {

    private NativeEnvironment environment;

    @BeforeAll
    void startEnvironment() {
        environment = NativeEnvironment.create("restart").start();
    }

    @AfterAll
    void stopEnvironment() {
        if (environment != null) {
            environment.close();
        }
    }

    @Test
    void dataSurvivesARestart() {
        List<String> report = NativeRestartCheck.run(environment);
        assertTrue(report.stream().noneMatch(line -> line.startsWith("FAIL")),
                String.join("\n", report));
        assertEquals("PASS  data after the restart", report.get(report.size() - 1));
    }
}
