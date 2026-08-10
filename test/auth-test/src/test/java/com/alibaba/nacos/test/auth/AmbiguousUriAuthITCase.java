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

package com.alibaba.nacos.test.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Authentication bypass regression tests for ambiguous request URI forms.
 *
 * @author Nacos
 */
public class AmbiguousUriAuthITCase extends AuthITCase {

    private static final String PROTECTED_PATH = "/v3/admin/core/namespace/list";

    @ParameterizedTest
    @ValueSource(strings = {
            "/n%61cos/v3/admin/core/namespace/list",
            "/%6Eacos/v3/admin/core/namespace/list",
            "/na%63os/v3/admin/core/namespace/list",
            "/naco%73/v3/admin/core/namespace/list",
            "/nacos/v3/admin/core/namespace/l%69st",
            "/nacos/v3/admin/core/namespace/%6Cist",
            "/nacos/v3/admin/core/namespace/lis%74",
            "/nacos/v3/admin/core/namespace/list?",
            "/nacos/v3/admin/core/namespace/list?foo=bar&answer=42",
            "/nacos/v3/admin/core/namespace/list?next=%2Fnacos%2Fv3%2Fadmin",
            "/nacos/v3/admin/core/namespace/list?foo=first&foo=second"
    })
    void testEquivalentRoutedUriIsAuthenticated(String path) throws Exception {
        assertDenied(get(SERVER_BASE_URL, path, null));
    }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("suspiciousRequestTargets")
    void testSuspiciousRequestTargetIsBlocked(String category, String requestTarget)
            throws Exception {
        assertBlocked(rawGet(requestTarget));
    }

    @Test
    void testCanonicalProtectedPathStillRequiresAuthentication() throws Exception {
        assertEquals(403, get(SERVER_BASE_URL, CONTEXT_PATH + PROTECTED_PATH, null).status());
    }

    private static Stream<Arguments> suspiciousRequestTargets() {
        return Stream.of(
                Arguments.of("matrix-param-context",
                        "/nacos;tenant=other/v3/admin/core/namespace/list"),
                Arguments.of("matrix-param-middle",
                        "/nacos/v3;ignored=true/admin/core/namespace/list"),
                Arguments.of("matrix-param-end",
                        "/nacos/v3/admin/core/namespace/list;ignored=true"),
                Arguments.of("encoded-semicolon",
                        "/nacos/v3/admin/core/namespace/list%3Bignored=true"),
                Arguments.of("duplicate-slash-context",
                        "/nacos//v3/admin/core/namespace/list"),
                Arguments.of("duplicate-slash-middle",
                        "/nacos/v3//admin/core/namespace/list"),
                Arguments.of("multiple-leading-slashes",
                        "//nacos/v3/admin/core/namespace/list"),
                Arguments.of("triple-slash",
                        "/nacos///v3/admin/core/namespace/list"),
                Arguments.of("trailing-slash",
                        "/nacos/v3/admin/core/namespace/list/"),
                Arguments.of("literal-current-segment",
                        "/nacos/v3/admin/core/namespace/./list"),
                Arguments.of("literal-parent-segment",
                        "/nacos/v3/admin/core/namespace/other/../list"),
                Arguments.of("encoded-current-segment",
                        "/nacos/v3/admin/core/namespace/%2e/list"),
                Arguments.of("encoded-parent-segment",
                        "/nacos/v3/admin/core/namespace/other/%2e%2e/list"),
                Arguments.of("mixed-encoded-parent-segment",
                        "/nacos/v3/admin/core/namespace/other/.%2e/list"),
                Arguments.of("double-encoded-dot-segment",
                        "/nacos/v3/admin/core/namespace/other/%252e%252e/list"),
                Arguments.of("encoded-forward-slash",
                        "/nacos/v3/admin/core/namespace%2Flist"),
                Arguments.of("lowercase-encoded-forward-slash",
                        "/nacos/v3/admin/core/namespace%2flist"),
                Arguments.of("double-encoded-forward-slash",
                        "/nacos/v3/admin/core/namespace%252Flist"),
                Arguments.of("encoded-backslash",
                        "/nacos/v3/admin/core/namespace%5Clist"),
                Arguments.of("lowercase-encoded-backslash",
                        "/nacos/v3/admin/core/namespace%5clist"),
                Arguments.of("double-encoded-backslash",
                        "/nacos/v3/admin/core/namespace%255Clist"),
                Arguments.of("literal-backslash-context",
                        "/nacos\\v3/admin/core/namespace/list"),
                Arguments.of("literal-backslash-endpoint",
                        "/nacos/v3/admin/core/namespace\\list"),
                Arguments.of("unicode-division-slash",
                        "/nacos/v3/admin/core/namespace%E2%88%95list"),
                Arguments.of("unicode-fraction-slash",
                        "/nacos/v3/admin/core/namespace%E2%81%84list"),
                Arguments.of("unicode-fullwidth-slash",
                        "/nacos/v3/admin/core/namespace%EF%BC%8Flist"),
                Arguments.of("double-encoded-context",
                        "/n%2561cos/v3/admin/core/namespace/list"),
                Arguments.of("double-encoded-controller-segment",
                        "/nacos/v3/admin/core/namespace/l%2569st"),
                Arguments.of("encoded-percent",
                        "/nacos/v3/admin/core/namespace%25/list"),
                Arguments.of("encoded-question-mark",
                        "/nacos/v3/admin/core/namespace%3Fignored/list"),
                Arguments.of("encoded-fragment-marker",
                        "/nacos/v3/admin/core/namespace%23ignored/list"),
                Arguments.of("encoded-space",
                        "/nacos/v3/admin/core/namespace%20/list"),
                Arguments.of("encoded-tab",
                        "/nacos/v3/admin/core/namespace%09/list"),
                Arguments.of("encoded-carriage-return",
                        "/nacos/v3/admin/core/namespace%0D/list"),
                Arguments.of("encoded-line-feed",
                        "/nacos/v3/admin/core/namespace%0A/list"),
                Arguments.of("encoded-null",
                        "/nacos/v3/admin/core/namespace%00/list"),
                Arguments.of("invalid-utf8-byte",
                        "/nacos/v3/admin/core/namespace%FF/list"),
                Arguments.of("overlong-utf8-slash",
                        "/nacos/v3/admin/core/namespace%C0%AFlist"),
                Arguments.of("invalid-utf8-sequence",
                        "/nacos/v3/admin/core/namespace%C3%28/list"),
                Arguments.of("utf8-surrogate",
                        "/nacos/v3/admin/core/namespace%ED%A0%80/list"),
                Arguments.of("malformed-percent-only",
                        "/nacos/v3/admin/core/namespace%/list"),
                Arguments.of("malformed-percent-short",
                        "/nacos/v3/admin/core/namespace%2/list"),
                Arguments.of("malformed-percent-non-hex",
                        "/nacos/v3/admin/core/namespace%GG/list"),
                Arguments.of("non-standard-unicode-escape",
                        "/n%u0061cos/v3/admin/core/namespace/list"),
                Arguments.of("absolute-form-request-target",
                        "http://127.0.0.1:" + NACOS_PORT
                                + "/nacos/v3/admin/core/namespace/list"),
                Arguments.of("asterisk-form-on-get", "*"),
                Arguments.of("userinfo-like-path",
                        "/nacos@other/v3/admin/core/namespace/list"),
                Arguments.of("case-variant-context",
                        "/NACOS/v3/admin/core/namespace/list"));
    }
}
