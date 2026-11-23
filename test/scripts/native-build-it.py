#!/usr/bin/env python3
# Copyright 1999-2026 Alibaba Group Holding Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Verify Native Maven preparation and packaging guards using an ordinary JDK.

Run after the ordinary release-nacos install. No Native compiler or database is required.
This checks AOT preparation, not Native executable or database runtime compatibility.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tempfile
import time
import zipfile


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if "=" in line and not line.startswith("#"))


def check_launcher(root, work):
    home = work / "launcher with spaces"
    for name in ("bin", "target", "conf"):
        (home / name).mkdir(parents=True, exist_ok=True)
    script = home / "bin/startup-native.sh"
    shutil.copy2(root / "distribution/bin/startup-native.sh", script)
    executable = home / "target/nacos-server"
    # Only capture arguments; this fixture is not a Nacos server or a Native runtime test.
    executable.write_text('#!/bin/bash\nprintf "%s\\n" "$@" > "$(dirname "$0")/arguments.txt"\n')
    executable.chmod(0o755)
    (home / "target/nacos-server.jar").write_text("Must not be selected")
    (home / "target/aot-manifest.properties").write_text(
        "standalone=true\ndeployment=MERGED\nfunctionMode=All\nembeddedStorage=false\n")
    environment = os.environ.copy()
    environment.pop("JAVA_HOME", None)
    environment["NACOS_NATIVE_OPTS"] = "-Xmx1g -Xms128m"
    arguments = home / "target/arguments.txt"

    def launch(*options):
        arguments.unlink(missing_ok=True)
        subprocess.run(["bash", str(script), *options], env=environment,
                       check=True, capture_output=True, timeout=15)
        deadline = time.monotonic() + 10
        while not arguments.exists() or "nacos.nacos" not in arguments.read_text():
            if time.monotonic() > deadline:
                raise AssertionError("Launcher fixture did not receive arguments")
            time.sleep(0.05)
        return arguments.read_text().splitlines()

    actual = launch()
    assert actual[:2] == ["-Xmx1g", "-Xms128m"]
    assert "-Dnacos.home=" + str(home) in actual
    assert "-Dspring.config.additional-location=file:" + str(home) + "/conf/" in actual
    assert "-Dnacos.standalone=true" in actual
    assert "-Dnacos.deployment.type=merged" in actual
    assert not any("nacos.functionMode=" in value for value in actual)
    actual = launch("-m", "cluster", "-d", "server", "-f", "microservice")
    assert "-Dnacos.standalone=false" in actual
    assert "-Dnacos.deployment.type=server" in actual
    assert "-Dnacos.functionMode=microservice" in actual
    for options in (("-m", "bad"), ("-d", "bad"), ("-f", "bad")):
        assert subprocess.run(["bash", str(script), *options], capture_output=True,
                              timeout=15).returncode != 0
    executable.unlink()
    assert subprocess.run(["bash", str(script)], capture_output=True, timeout=15).returncode != 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven", default="mvn")
    parser.add_argument("--maven-arg", action="append", default=[])
    parser.add_argument("--work-dir", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    work = args.work_dir or Path(tempfile.mkdtemp(prefix="nacos-native-build-it-"))
    work.mkdir(parents=True, exist_ok=True)
    work = work.resolve()
    native = root / "bootstrap/target/native"
    results = []
    command = [args.maven, "-B", *args.maven_arg, "-Dmaven.test.skip=true"]

    def run(name, options, failure=None):
        with (work / (name + ".log")).open("w") as log:
            result = subprocess.run(command + options, cwd=root, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=300)
        output = (work / (name + ".log")).read_text(errors="replace")
        assert (result.returncode == 0) == (failure is None), (name, result.returncode)
        if failure:
            assert failure in output, name
        results.append({"case": name, "passed": True})
        print(name + " PASS", flush=True)

    def configuration(name, platform, registry):
        path = work / (name + ".properties")
        path.write_text("spring.sql.init.platform=" + platform + "\n" + "\n".join(
            key + "=" + str(registry).lower() for key in (
                "nacos.ai.mcp.registry.enabled", "nacos.ai.skill.registry.enabled",
                "nacos.ai.ard.enabled")) + "\n")
        # Catch Ant copy's timestamp-based stale-configuration behavior.
        os.utime(path, (1, 1))
        return "-Dnacos.native.config=" + str(path)

    # Native preparation must leave the ordinary JVM application artifact unchanged.
    jar = root / "bootstrap/target/nacos-server.jar"
    before = hashlib.sha256(jar.read_bytes()).digest()
    common = ["-Pnative,!dev", "-pl", "bootstrap", "-DskipNativeBuild=true", "package"]
    saved = work / "previous-native-output"
    assert not saved.exists(), "Use a fresh work directory"
    if native.exists():
        shutil.move(native, saved)
    try:
        run("merged-derby", common + [configuration("derby", "derby", True),
            "-Dnacos.native.standalone=true", "-Dnacos.native.derby.experimental=true"])
        manifest = properties(native / "aot-manifest.properties")
        assert all(key in manifest for key in ("core", "web", "console", "ai-registry"))
        assert manifest["deployment"] == "MERGED" and manifest["datasource"] == "derby"
        assert "RuntimeClassLoading" in (native / "options.args").read_text()
        runtime = shlex.split((native / "classpath.args").read_text())[1]
        assert not any(part in runtime for part in ("test-classes", "tool-classes", "/junit/", "/mockito/"))
        assert list((native / "classes").rglob("*__CoreApplicationContextInitializer.class"))
        assert not (native / "classes/com/alibaba/nacos/bootstrap/NacosNativeBuild.class").exists()

        source = work / "CustomDialect.java"
        source.write_text("package sample; public final class CustomDialect extends "
            "com.alibaba.nacos.plugin.datasource.impl.dialect.DefaultDatabaseDialect { "
            'public String getType() { return "sample"; } }')
        classes = work / "plugin-classes"
        classes.mkdir()
        javac = Path(os.environ["JAVA_HOME"]) / "bin/javac"
        subprocess.run([str(javac), "--release", "17", "-cp", runtime, "-d", str(classes),
                        str(source)], check=True, timeout=30)
        plugins = work / "plugins with spaces"
        plugins.mkdir()
        plugin = plugins / "custom dialect.jar"
        with zipfile.ZipFile(plugin, "w") as archive:
            archive.write(classes / "sample/CustomDialect.class", "sample/CustomDialect.class")
            archive.writestr("META-INF/services/com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect",
                             "sample.CustomDialect\n")
            archive.writestr("META-INF/native-image/sample/plugin/reachability-metadata.json",
                             json.dumps({"reflection": [{"type": "sample.CustomDialect"}]}))
        run("server-mysql-plugin", common + [configuration("mysql", "mysql", False),
            "-Dnacos.native.deployment=server", "-Dnacos.native.plugins=" + str(plugins)])
        manifest = properties(native / "aot-manifest.properties")
        assert manifest["deployment"] == "SERVER" and manifest["datasource"] == "mysql"
        assert "console" not in manifest and "ai-registry" not in manifest
        assert "sample.CustomDialect" in manifest["spi.com.alibaba.nacos.plugin.datasource.dialect.DatabaseDialect"]
        assert str(plugin) in shlex.split((native / "classpath.args").read_text())[1].split(os.pathsep)
        assert "RuntimeClassLoading" not in (native / "options.args").read_text()
        run("console-postgresql", common + [configuration("postgresql", "postgresql", False),
            "-Dnacos.native.deployment=console"])
        manifest = properties(native / "aot-manifest.properties")
        assert manifest["deployment"] == "CONSOLE" and manifest["datasource"] == "postgresql"
        assert "core" not in manifest and "web" not in manifest
        assert not (native / "aot/core").exists() and not (native / "aot/web").exists()
        assert not list((native / "classes").rglob("*__CoreApplicationContextInitializer.class"))
        run("derby-opt-in-required", common + [configuration("derby", "derby", True),
            "-Dnacos.native.standalone=true"], "Embedded Derby Native requires")
        release = ["-pl", "distribution", "package"]
        run("release-requires-native", release + ["-Prelease-native"],
            "release-native requires -Pnative,release-native")
        for flag in ("nativeDryRun", "skipNativeBuild"):
            run("release-rejects-" + flag, release + ["-Pnative,release-native", "-D" + flag + "=true"],
                "nativeDryRun and skipNativeBuild cannot produce a release")
        assert hashlib.sha256(jar.read_bytes()).digest() == before
        with zipfile.ZipFile(jar) as archive:
            assert not any("NacosNativeBuild" in name or "nacos/aot/initializers.properties" in name
                           for name in archive.namelist())
        assert not (root / "bootstrap/target/classes/META-INF/nacos/aot/initializers.properties").exists()
        check_launcher(root, work)
        results.append({"case": "ordinary-artifact-and-launcher", "passed": True})
    finally:
        (work / "results.json").write_text(json.dumps(results, indent=2) + "\n")
        if native.exists():
            shutil.move(native, work / "last-native-output")
        if saved.exists():
            shutil.move(saved, native)
    print("Native build IT passed; logs: " + str(work), flush=True)


if __name__ == "__main__":
    main()
