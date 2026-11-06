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

import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * A Kubernetes cluster for the tests of the Kubernetes sync: a k3s container of Testcontainers.
 * The Server runs on the host and reaches the API server through the port that the container
 * publishes, with the kubeconfig that {@link #writeKubeConfig(Path)} writes. The tests create
 * their objects with {@code kubectl} inside the container, so that they need no Kubernetes client
 * of their own.
 *
 * @author Nacos
 */
public final class NativeKubernetes implements AutoCloseable {

    private static final String IMAGE = "rancher/k3s:v1.34.13-k3s1";

    private static final String MANIFEST = "/tmp/native-test-manifest.yaml";

    private K3sContainer container;

    /**
     * Starts the cluster and waits until its API server answers.
     *
     * @return the cluster
     */
    public static NativeKubernetes start() {
        NativeKubernetes kubernetes = new NativeKubernetes();
        K3sContainer container = new K3sContainer(DockerImageName.parse(IMAGE));
        // The command of Testcontainers, with three more options for the kubelet. A rootless Docker
        // may not write the kernel flags that the kubelet sets, and a cgroup v2 that the user was
        // not given the controllers of does not let it manage the cgroups of the pods. The tests
        // run no pods, so the kubelet does without both, and the cluster starts on any Docker.
        container.withCommand("server", "--disable=traefik", "--tls-san=" + container.getHost(),
                "--kubelet-arg=feature-gates=KubeletInUserNamespace=true",
                "--kubelet-arg=cgroups-per-qos=false", "--kubelet-arg=enforce-node-allocatable=");
        container.start();
        kubernetes.container = container;
        return kubernetes;
    }

    /**
     * Writes the kubeconfig of the cluster, with the address that the host reaches it by.
     *
     * @param file the file to write
     * @return the file
     */
    public Path writeKubeConfig(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, container.getKubeConfigYaml(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Applies a manifest.
     *
     * @param manifest the YAML of one or more objects
     */
    public void apply(String manifest) {
        container.copyFileToContainer(Transferable.of(manifest), MANIFEST);
        kubectl("apply", "-f", MANIFEST);
    }

    /**
     * Runs {@code kubectl} in the container and fails if it fails.
     *
     * @param arguments the arguments
     * @return its output
     */
    public String kubectl(String... arguments) {
        List<String> command = new java.util.ArrayList<>();
        command.add("kubectl");
        command.addAll(List.of(arguments));
        try {
            ExecResult result = container.execInContainer(command.toArray(new String[0]));
            if (result.getExitCode() != 0) {
                throw new IllegalStateException(String.join(" ", command) + " failed with "
                        + result.getExitCode() + ": " + result.getStderr());
            }
            return result.getStdout();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * The log of the container.
     *
     * @return the log, empty before the start
     */
    public String logs() {
        return container == null ? "" : container.getLogs();
    }

    @Override
    public void close() {
        if (container != null) {
            container.stop();
            container = null;
        }
    }
}
