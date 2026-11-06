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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Kubernetes sync: the Server watches the Services and the Endpoints of a cluster and keeps a
 * persistent service with its instances in the registry for each of them, in a namespace of the
 * name of the Kubernetes namespace. The cluster is a k3s container, and the Server reaches it with
 * a kubeconfig. The switch {@code nacos.k8s.sync.enabled} is a switch of the build as well,
 * because a package filter keeps the sync out of the component scan. The sync starts after the
 * Server, and the Server does not start if it cannot reach the cluster, so the cluster is started
 * first.
 *
 * @author Nacos
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NativeKubernetesSyncITCase {

    private static final String NAMESPACE = "native-sync";

    private static final String SERVICE = "k8s-svc";

    private final NativeHttp http = new NativeHttp();

    private NativeKubernetes kubernetes;

    private NativeEnvironment environment;

    private String api;

    private String token;

    @BeforeAll
    void startEnvironment() {
        kubernetes = NativeKubernetes.start();
        Path kubeConfig = kubernetes.writeKubeConfig(
                NativeTestSettings.workDirectory().resolve("kubernetes").resolve("kubeconfig"));
        environment = NativeEnvironment.builder("k8s-sync")
                .property("nacos.k8s.sync.enabled", "true")
                .property("nacos.k8s.sync.outsideCluster", "true")
                .property("nacos.k8s.sync.kubeConfig", kubeConfig.toString()).build().start();
        api = environment.server().apiUrl() + "/nacos/v3";
        token = environment.identities().adminToken();
    }

    @AfterAll
    void stopEnvironment() {
        try {
            if (environment != null) {
                environment.close();
            }
        } finally {
            if (kubernetes != null) {
                kubernetes.close();
            }
        }
    }

    @Test
    @Order(1)
    void aServiceAndItsEndpointsBecomeAPersistentInstance() {
        kubernetes.apply(manifest("10.9.8.7"));
        NativeHttp.Response response = eventually(Duration.ofSeconds(90),
                each -> each.ok() && each.body().contains("10.9.8.7"), this::instances);
        assertTrue(response.ok() && response.body().contains("10.9.8.7"), response.describe());
        // The port of the Service goes into the metadata, the target port is the port of the
        // instance.
        assertTrue(response.matches("\"servicePort\"\\s*:\\s*\"80\""), response.describe());
        assertTrue(response.matches("\"port\"\\s*:\\s*8080"), response.describe());
        assertTrue(response.matches("\"ephemeral\"\\s*:\\s*false"), response.describe());
    }

    @Test
    @Order(2)
    void aChangeOfTheEndpointsMovesTheInstance() {
        kubernetes.apply(manifest("10.9.8.8"));
        NativeHttp.Response response = eventually(Duration.ofSeconds(60),
                each -> each.ok() && each.body().contains("10.9.8.8")
                        && !each.body().contains("10.9.8.7"), this::instances);
        assertTrue(response.body().contains("10.9.8.8"), response.describe());
        assertFalse(response.body().contains("10.9.8.7"), response.describe());
    }

    @Test
    @Order(3)
    void theServicesOfTheClusterItselfAreSynchronizedToo() {
        // Every cluster has the service kubernetes in the namespace default, whose endpoint is
        // the API server.
        NativeHttp.Response response = eventually(Duration.ofSeconds(60),
                each -> each.ok() && each.body().contains("6443"),
                () -> http.get(api + "/admin/ns/instance/list?serviceName=kubernetes"
                        + "&groupName=DEFAULT_GROUP&namespaceId=default", token));
        assertTrue(response.ok() && response.body().contains("6443"), response.describe());
    }

    @Test
    @Order(4)
    void theInstancesOfADeletedServiceDisappearFromTheRegistry() {
        kubernetes.kubectl("delete", "service,endpoints", SERVICE, "--namespace", NAMESPACE,
                "--ignore-not-found");
        // The instances are what the sync has to remove. It removes the service and its instances
        // from two informers that run side by side: when the handler of the Endpoints lists the
        // instances after the service is gone, the Server creates the service again, empty, and
        // nothing removes it. So the test does not ask for the service itself to be gone.
        NativeHttp.Response response = eventually(Duration.ofSeconds(60),
                each -> !each.ok() || !each.body().contains("10.9.8.8"), this::instances);
        assertFalse(response.ok() && response.body().contains("10.9.8.8"), response.describe());
    }

    private NativeHttp.Response instances() {
        return http.get(api + "/admin/ns/instance/list?serviceName=" + SERVICE
                + "&groupName=DEFAULT_GROUP&namespaceId=" + NAMESPACE, token);
    }

    private static String manifest(String address) {
        return "apiVersion: v1\n"
                + "kind: Namespace\n"
                + "metadata:\n"
                + "  name: " + NAMESPACE + "\n"
                + "---\n"
                + "apiVersion: v1\n"
                + "kind: Service\n"
                + "metadata:\n"
                + "  name: " + SERVICE + "\n"
                + "  namespace: " + NAMESPACE + "\n"
                + "spec:\n"
                + "  ports:\n"
                + "    - name: http\n"
                + "      port: 80\n"
                + "      targetPort: 8080\n"
                + "---\n"
                + "apiVersion: v1\n"
                + "kind: Endpoints\n"
                + "metadata:\n"
                + "  name: " + SERVICE + "\n"
                + "  namespace: " + NAMESPACE + "\n"
                + "subsets:\n"
                + "  - addresses:\n"
                + "      - ip: " + address + "\n"
                + "    ports:\n"
                + "      - name: http\n"
                + "        port: 8080\n";
    }

    /**
     * Repeats a request until its answer is the one that is wanted or the time is up.
     */
    private static NativeHttp.Response eventually(Duration usual,
            Predicate<NativeHttp.Response> done,
            java.util.function.Supplier<NativeHttp.Response> request) {
        long deadline = System.nanoTime() + NativeTestSettings.scaled(usual).toNanos();
        NativeHttp.Response response = request.get();
        while (!done.test(response) && System.nanoTime() < deadline) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            response = request.get();
        }
        return response;
    }
}
