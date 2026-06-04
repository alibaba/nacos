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

package com.alibaba.nacos.test.sdk.config;

import com.alibaba.nacos.api.config.ConfigQueryResult;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.config.filter.AbstractConfigFilter;
import com.alibaba.nacos.api.config.filter.IConfigFilterChain;
import com.alibaba.nacos.api.config.filter.IConfigRequest;
import com.alibaba.nacos.api.config.filter.IConfigResponse;
import com.alibaba.nacos.api.config.listener.AbstractFuzzyWatchEventWatcher;
import com.alibaba.nacos.api.config.listener.ConfigFuzzyWatchChangeEvent;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.test.sdk.JavaSdkBaseITCase;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static com.alibaba.nacos.client.config.common.ConfigConstants.CONTENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for Java SDK {@link ConfigService}.
 *
 * <p>The full scenario matrix and remaining gaps are recorded in
 * {@code test/java-sdk-test/JAVA_SDK_IT_SCENARIOS.md}.
 *
 * <p>Scenario coverage:
 * <ul>
 *     <li>Expected capability: publish, query, query-with-result, CAS update, and remove config
 *     through the public Java SDK factory; standalone listener registration receives later
 *     changes.</li>
 *     <li>Boundary/validation: blank group uses the default group, missing config returns
 *     {@code null}, missing query result has an empty result shape, bad CAS md5 is rejected,
 *     CAS against missing config creates data, empty CAS md5 behaves as normal publish,
 *     missing removal is idempotent, and missing identity/content fields throw
 *     {@link NacosException}; unknown config type is accepted as a compatibility boundary and
 *     remains queryable.</li>
 *     <li>Error handling: invalid required fields and group names are mapped to controlled SDK
 *     exceptions.</li>
 *     <li>Listener/error handling: {@code getConfigAndSignListener} returns the current value,
 *     delivers later updates, standalone listener receives updates, listener removal stops later
 *     callbacks, null listener input is rejected before silent registration, and listener cleanup
 *     plus SDK shutdown are safe; fuzzy watch returns matched keys, receives add/delete events,
 *     and stops callbacks after cancel.</li>
 *     <li>Filter/type behavior: valid non-text config types are preserved in query result
 *     metadata, and a public SDK config filter can transform publish request content and query
 *     response content.</li>
 * </ul>
 *
 * @author xiweng.yy
 */
public class ConfigServiceJavaSdkITCase extends JavaSdkBaseITCase {

    @Test
    public void testPublishQueryCasAndRemoveConfig() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("lifecycle");
        String group = randomGroup("config");
        String firstContent = "sdk.config.first=true";
        String secondContent = "sdk.config.second=true";
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, firstContent, ConfigType.TEXT.getType()));
        waitUntilConfigEquals(configService, dataId, group, firstContent);
        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group, DEFAULT_TIMEOUT_MS);
        assertEquals(firstContent, queryResult.getContent());
        assertNotNull(queryResult.getMd5(), queryResult.toString());

        assertFalse(configService.publishConfigCas(dataId, group, "bad-cas-content", "bad-md5",
                ConfigType.TEXT.getType()));
        assertEquals(firstContent, configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        assertTrue(configService.publishConfigCas(dataId, group, secondContent, queryResult.getMd5(),
                ConfigType.TEXT.getType()));
        waitUntilConfigEquals(configService, dataId, group, secondContent);

        assertTrue(configService.removeConfig(dataId, group));
        waitUntil("removed config should be absent", () -> null == configService.getConfig(dataId, group,
                DEFAULT_TIMEOUT_MS));
        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }

    @Test
    public void testMissingConfigResultAndRemoveAreEmptyAndIdempotent() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("missing-result");
        String group = randomGroup("config");

        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group,
                DEFAULT_TIMEOUT_MS);
        assertNotNull(queryResult, "missing config should still return a result object");
        assertNull(queryResult.getContent(), queryResult.toString());
        assertNull(queryResult.getMd5(), queryResult.toString());
        assertNull(queryResult.getConfigType(), queryResult.toString());

        assertTrue(configService.removeConfig(dataId, group),
                "server-side remove of an absent config is idempotent");
        assertNull(configService.getConfig(dataId, group, DEFAULT_TIMEOUT_MS));
    }

    @Test
    public void testCasBoundaryForMissingAndEmptyMd5() throws Exception {
        ConfigService configService = createConfigService();
        String missingDataId = randomDataId("missing-cas");
        String emptyMd5DataId = randomDataId("empty-cas");
        String group = randomGroup("config");
        addCleanup(() -> configService.removeConfig(missingDataId, group));
        addCleanup(() -> configService.removeConfig(emptyMd5DataId, group));

        assertTrue(configService.publishConfigCas(missingDataId, group, "missing.cas",
                "missing-md5", ConfigType.TEXT.getType()));
        waitUntilConfigEquals(configService, missingDataId, group, "missing.cas");

        assertTrue(configService.publishConfigCas(emptyMd5DataId, group, "empty.md5.cas", "",
                ConfigType.TEXT.getType()));
        waitUntilConfigEquals(configService, emptyMd5DataId, group, "empty.md5.cas");
    }

    @Test
    public void testGetConfigAndSignListenerReceivesUpdates() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("listener");
        String group = randomGroup("config");
        String firstContent = "sdk.listener.first=true";
        String secondContent = "sdk.listener.second=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                if (secondContent.equals(configInfo)) {
                    received.set(configInfo);
                    latch.countDown();
                }
            }
        };
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, firstContent));
        waitUntilConfigEquals(configService, dataId, group, firstContent);
        assertEquals(firstContent, configService.getConfigAndSignListener(dataId, group, DEFAULT_TIMEOUT_MS,
                listener));
        assertTrue(configService.publishConfig(dataId, group, secondContent));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "listener should receive updated config");
        assertEquals(secondContent, received.get());
    }

    @Test
    public void testAddListenerReceivesPublishedUpdate() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("add-listener");
        String group = randomGroup("config");
        String content = "sdk.listener.add=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = listenerForContent(content, latch, received);
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));

        configService.addListener(dataId, group, listener);
        assertTrue(configService.publishConfig(dataId, group, content));

        assertTrue(latch.await(10, TimeUnit.SECONDS), "standalone listener should receive update");
        assertEquals(content, received.get());
    }

    @Test
    public void testRemoveListenerStopsLaterCallbacks() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("remove-listener");
        String group = randomGroup("config");
        String firstContent = "sdk.listener.before-remove=true";
        String secondContent = "sdk.listener.after-remove=true";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> received = new AtomicReference<>();
        Listener listener = listenerForContent(secondContent, latch, received);
        addCleanup(() -> configService.removeListener(dataId, group, listener));
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, firstContent));
        configService.addListener(dataId, group, listener);
        configService.removeListener(dataId, group, listener);
        assertTrue(configService.publishConfig(dataId, group, secondContent));

        assertFalse(latch.await(2, TimeUnit.SECONDS),
                "removed listener should not receive later update");
        assertNull(received.get());
        waitUntilConfigEquals(configService, dataId, group, secondContent);
    }

    @Test
    public void testNullConfigListenerIsRejected() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("null-listener");
        String group = randomGroup("config");
        String content = "sdk.listener.null-boundary=true";
        Listener validListener = listenerForContent("unused", new CountDownLatch(1),
                new AtomicReference<>());
        addCleanup(() -> configService.removeListener(dataId, group, validListener));
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, content));
        waitUntilConfigEquals(configService, dataId, group, content);

        IllegalArgumentException addListenerFailure = assertThrows(IllegalArgumentException.class,
                () -> configService.addListener(dataId, group, null));
        assertEquals("listener is null", addListenerFailure.getMessage());

        IllegalArgumentException signListenerFailure = assertThrows(IllegalArgumentException.class,
                () -> configService.getConfigAndSignListener(dataId, group, DEFAULT_TIMEOUT_MS, null));
        assertEquals("listener is null", signListenerFailure.getMessage());

        configService.addListener(dataId, group, validListener);
        IllegalArgumentException removeListenerFailure = assertThrows(IllegalArgumentException.class,
                () -> configService.removeListener(dataId, group, null));
        assertEquals("listener is null", removeListenerFailure.getMessage());
    }

    @Test
    public void testConfigValidationAndDefaultGroupBoundary() throws Exception {
        ConfigService configService = createConfigService();
        String group = randomGroup("invalid");
        String defaultGroupDataId = randomDataId("default-group");
        String invalidTypeDataId = randomDataId("invalid-type");
        addCleanup(() -> configService.removeConfig(defaultGroupDataId, Constants.DEFAULT_GROUP));
        addCleanup(() -> configService.removeConfig(invalidTypeDataId, group));

        NacosException missingDataId = assertThrows(NacosException.class,
                () -> configService.getConfig("", group, DEFAULT_TIMEOUT_MS));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, missingDataId.getErrCode(), missingDataId.toString());

        NacosException missingContent = assertThrows(NacosException.class,
                () -> configService.publishConfig(randomDataId("invalid"), group, ""));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, missingContent.getErrCode(), missingContent.toString());

        NacosException invalidGroup = assertThrows(NacosException.class,
                () -> configService.getConfig(randomDataId("invalid"), "bad/group", DEFAULT_TIMEOUT_MS));
        assertEquals(NacosException.CLIENT_INVALID_PARAM, invalidGroup.getErrCode(), invalidGroup.toString());

        assertTrue(configService.publishConfig(defaultGroupDataId, "", "default.group.boundary"));
        waitUntilConfigEquals(configService, defaultGroupDataId, Constants.DEFAULT_GROUP,
                "default.group.boundary");

        assertTrue(configService.publishConfig(invalidTypeDataId, group, "unknown.type.content",
                "bad-type"));
        waitUntilConfigEquals(configService, invalidTypeDataId, group, "unknown.type.content");
    }

    @Test
    public void testValidJsonTypeIsPreservedInQueryResult() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("json-type");
        String group = randomGroup("config");
        String content = "{\"sdk\":\"json\",\"enabled\":true}";
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, content, ConfigType.JSON.getType()));
        waitUntilConfigEquals(configService, dataId, group, content);

        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group,
                DEFAULT_TIMEOUT_MS);
        assertEquals(content, queryResult.getContent());
        assertEquals(ConfigType.JSON.getType(), queryResult.getConfigType(),
                queryResult.toString());
        assertNotNull(queryResult.getMd5(), queryResult.toString());
    }

    @Test
    public void testConfigFilterTransformsPublishAndQueryContent() throws Exception {
        ConfigService configService = createConfigService();
        String dataId = randomDataId("filter");
        String group = randomGroup("config");
        String originalContent = "sdk.filter.original=true";
        String storedContent = originalContent + TransformingConfigFilter.PUBLISH_SUFFIX;
        String visibleContent = storedContent + TransformingConfigFilter.QUERY_SUFFIX;
        configService.addConfigFilter(new TransformingConfigFilter());
        addCleanup(() -> configService.removeConfig(dataId, group));

        assertTrue(configService.publishConfig(dataId, group, originalContent));
        waitUntilConfigEquals(configService, dataId, group, visibleContent);

        ConfigQueryResult queryResult = configService.getConfigWithResult(dataId, group,
                DEFAULT_TIMEOUT_MS);
        assertEquals(visibleContent, queryResult.getContent());
        assertNotNull(queryResult.getMd5(), queryResult.toString());
    }

    @Test
    public void testFuzzyWatchReturnsKeysAndStopsAfterCancel() throws Exception {
        ConfigService configService = createConfigService();
        String group = randomGroup("fuzzy");
        String dataIdPrefix = randomDataId("fuzzy").replace(".data", "");
        String existingDataId = dataIdPrefix + "-existing.data";
        String addedDataId = dataIdPrefix + "-added.data";
        String deletedDataId = dataIdPrefix + "-delete.data";
        String afterCancelDataId = dataIdPrefix + "-after-cancel.data";
        String ignoredDataId = randomDataId("ignored-fuzzy");
        CountDownLatch addLatch = new CountDownLatch(1);
        CountDownLatch deleteLatch = new CountDownLatch(1);
        CountDownLatch afterCancelLatch = new CountDownLatch(1);
        AtomicReference<ConfigFuzzyWatchChangeEvent> addEvent = new AtomicReference<>();
        AtomicReference<ConfigFuzzyWatchChangeEvent> deleteEvent = new AtomicReference<>();
        AbstractFuzzyWatchEventWatcher watcher = new AbstractFuzzyWatchEventWatcher() {
            @Override
            public void onEvent(ConfigFuzzyWatchChangeEvent event) {
                if (addedDataId.equals(event.getDataId())
                        && Constants.ConfigChangedType.ADD_CONFIG.equals(event.getChangedType())) {
                    addEvent.set(event);
                    addLatch.countDown();
                }
                if (deletedDataId.equals(event.getDataId())
                        && Constants.ConfigChangedType.DELETE_CONFIG.equals(event.getChangedType())) {
                    deleteEvent.set(event);
                    deleteLatch.countDown();
                }
                if (afterCancelDataId.equals(event.getDataId())) {
                    afterCancelLatch.countDown();
                }
            }
        };
        addCleanup(() -> configService.cancelFuzzyWatch(dataIdPrefix + "*", group, watcher));
        addCleanup(() -> configService.removeConfig(afterCancelDataId, group));
        addCleanup(() -> configService.removeConfig(deletedDataId, group));
        addCleanup(() -> configService.removeConfig(addedDataId, group));
        addCleanup(() -> configService.removeConfig(ignoredDataId, group));
        addCleanup(() -> configService.removeConfig(existingDataId, group));

        assertTrue(configService.publishConfig(existingDataId, group, "fuzzy.existing"));
        assertTrue(configService.publishConfig(ignoredDataId, group, "fuzzy.ignored"));
        waitUntilConfigEquals(configService, existingDataId, group, "fuzzy.existing");
        Future<Set<String>> groupKeysFuture = configService.fuzzyWatchWithGroupKeys(
                dataIdPrefix + "*", group, watcher);

        Set<String> groupKeys = groupKeysFuture.get(10, TimeUnit.SECONDS);
        assertTrue(groupKeys.stream().anyMatch(each -> each.contains(existingDataId)),
                groupKeys.toString());
        assertFalse(groupKeys.stream().anyMatch(each -> each.contains(ignoredDataId)),
                groupKeys.toString());

        assertTrue(configService.publishConfig(addedDataId, group, "fuzzy.added"));
        assertTrue(addLatch.await(10, TimeUnit.SECONDS),
                "fuzzy watcher should receive add event for matched config");
        assertEquals(group, addEvent.get().getGroup(), addEvent.get().toString());

        assertTrue(configService.publishConfig(deletedDataId, group, "fuzzy.deleted"));
        waitUntilConfigEquals(configService, deletedDataId, group, "fuzzy.deleted");
        assertTrue(configService.removeConfig(deletedDataId, group));
        assertTrue(deleteLatch.await(10, TimeUnit.SECONDS),
                "fuzzy watcher should receive delete event for matched config");
        assertEquals(group, deleteEvent.get().getGroup(), deleteEvent.get().toString());

        configService.cancelFuzzyWatch(dataIdPrefix + "*", group, watcher);
        assertTrue(configService.publishConfig(afterCancelDataId, group, "fuzzy.after.cancel"));
        assertFalse(afterCancelLatch.await(2, TimeUnit.SECONDS),
                "canceled fuzzy watcher should not receive later events");
    }

    private Listener listenerForContent(String expectedContent, CountDownLatch latch,
            AtomicReference<String> received) {
        return new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
                if (expectedContent.equals(configInfo)) {
                    received.set(configInfo);
                    latch.countDown();
                }
            }
        };
    }

    private void waitUntilConfigEquals(ConfigService configService, String dataId, String group,
            String expectedContent) throws Exception {
        waitUntil("config should become queryable, dataId=" + dataId + ", group=" + group,
                () -> expectedContent.equals(configService.getConfig(dataId, group,
                        DEFAULT_TIMEOUT_MS)));
    }

    private static class TransformingConfigFilter extends AbstractConfigFilter {

        static final String PUBLISH_SUFFIX = "|request-filter";

        static final String QUERY_SUFFIX = "|response-filter";

        @Override
        public void init(Properties properties) {
        }

        @Override
        public void doFilter(IConfigRequest request, IConfigResponse response,
                IConfigFilterChain filterChain) throws NacosException {
            if (null != request) {
                request.putParameter(CONTENT, request.getParameter(CONTENT) + PUBLISH_SUFFIX);
            }
            filterChain.doFilter(request, response);
            if (null != response && null != response.getParameter(CONTENT)) {
                response.putParameter(CONTENT, response.getParameter(CONTENT) + QUERY_SUFFIX);
            }
        }

        @Override
        public int getOrder() {
            return 0;
        }

        @Override
        public String getFilterName() {
            return "javaSdkItTransformingConfigFilter";
        }
    }
}
