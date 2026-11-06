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

"""Curate and verify the GraalVM reachability metadata bundled with the Nacos Server.

``refresh`` turns the raw output of the Native Image tracing agent into the committed
``reachability-metadata.json`` files. It merges several agent runs, drops entries that must not be
shipped (see POLICY below), prunes classes and resources that are not on the Native classpath,
and writes the result in a stable, line-oriented order so that reviews and diffs stay small.

The committed metadata is not one file but one file per owner below ``META-INF/native-image``:
``<groupId>/<artifactId>/reachability-metadata.json`` for each third-party artifact, ``jdk/runtime``
for the classes of the JDK, and ``com.alibaba.nacos/nacos-bootstrap`` for everything that Nacos
builds itself. The owner of an entry is the artifact whose jar on the Native classpath contains the
class or the resource, so ``refresh`` needs that classpath and nobody sorts entries by hand.

``check`` verifies the committed files without needing GraalVM or a database. CI runs it from
``native-build-it.py``. With ``--classpath`` it also verifies that every entry sits in the file of
its owner.

Every entry carries a ``typeReached`` condition, and the condition depends on the owner of the entry
(see CONDITION_GROUPS): the Server entry point for what Nacos builds and for the JDK, and for a
third-party artifact the type that makes the Server use it. ``refresh`` sets the condition of every
entry, whatever the inputs carry; ``check`` rejects an entry that another condition guards.

``collect-missing`` closes the loop from the other side: an executable built with
``--exact-reachability-metadata`` and started with ``-XX:MissingRegistrationReportingMode=Warn``
prints every dynamic access that has no metadata. This command turns such logs into the same
format as the agent output, so ``refresh`` can merge them. It reads the reports about classes,
methods and constructors that were looked up or invoked, fields, unsafe allocation,
serialization, resources and resource bundles. Reports about lambda-proxy classes are counted
and skipped. A report it does not understand is printed as ``UNPARSED``, and the ``exact``
variant of the Native CI job fails on it instead of ignoring a gap that it cannot name.

Typical refresh, after tracing the Server with the exact classpath of the Native build. The
committed metadata is an input as well, because some of its entries are in no trace::

    python3 test/scripts/native-metadata.py refresh \\
        --classpath bootstrap/target/native/classpath.args \\
        bootstrap/src/main/resources/META-INF/native-image /path/to/trace-run-1 /path/to/trace-run-2

The files are written below ``bootstrap/src/main/resources/META-INF/native-image``, or below the
directory of ``--output-dir``. ``--output FILE`` writes one merged file instead; the Native CI job
uses it to list the registrations that an exact-mode build reports as missing. That file is a list
for a reader: its entries carry the Server entry point as a provisional condition, and a refresh
into the bundle sets the condition of each entry from its owner.
"""
import argparse
import contextlib
import io
import json
import os
from pathlib import Path
import re
import sys
import zipfile

DEFAULT_BUNDLE = "bootstrap/src/main/resources/META-INF/native-image"
METADATA_NAME = "reachability-metadata.json"
SECTIONS = ("reflection", "resources")

# The owners that are not a third-party artifact. Everything that Nacos builds, whatever module it
# comes from, belongs to the first; the classes of the JDK, which are on no classpath, to the second.
# Both are also the names of the folders that hold their files. Folders are not versioned: the
# dependencies come from a managed set of versions, a file would have to move with every bump, and
# GraalVM does not look at the name of a folder.
NACOS_OWNER = "com.alibaba.nacos/nacos-bootstrap"
JDK_OWNER = "jdk/runtime"

# GraalVM recommends that every entry be conditional. An entry with a typeReached condition is
# included in the image if the type is reachable at build time, and it is available at run time
# once the type is reached, that is, right before the initialization of its class starts (or that of
# a subtype); until then every dynamic access that the entry serves behaves as if the entry did not
# exist. So the condition type of an entry must be initialized before the first access that the
# entry serves, and a class that is only named by a class literal is never initialized.
#
# What Nacos builds and the JDK classes that its libraries reach: the Server entry point, which is
# initialized before any other Server code runs.
SERVER_ENTRY_POINT = "com.alibaba.nacos.bootstrap.NacosBootstrap"

# A third-party artifact: the type that makes the Server use it. It is often not a type of the
# artifact itself, because something else usually looks up the classes of an artifact before any
# of them is initialized. test/scripts/native-condition-order.py compares each type below with the
# order in which a run of the Server (JVM, the classpath of the Native build) loaded and initialized
# its classes: the type is initialized before the first class of the entries of its owners is
# loaded, apart from the owners of REASONED_CONDITIONS. An owner without a row stops a refresh; add
# it after such a comparison, and let the 'exact' variant of the Native CI job confirm it.
SPRING_BOOT = "org.springframework.boot.SpringApplication"
CONDITION_GROUPS = (
    # (condition type, owners)
    # Spring Boot looks up the classes of these artifacts while it builds the application context:
    # the types of beans, of their configuration and of the annotations it reads, and optional
    # libraries that it checks for. Nacos creates the SpringApplication before it uses any of them.
    # Every artifact of the Spring family is covered by owner_condition().
    (SPRING_BOOT, (
        "com.fasterxml.jackson.core/jackson-core",
        "com.fasterxml.jackson.core/jackson-databind",
        "com.fasterxml.jackson.dataformat/jackson-dataformat-yaml",
        "com.fasterxml.jackson.datatype/jackson-datatype-jsr310",
        "com.google.code.gson/gson",
        "com.zaxxer/HikariCP",
        "io.agentscope/agentscope-extensions-studio",
        "io.grpc/grpc-api",
        "io.micrometer/micrometer-commons",
        "io.micrometer/micrometer-core",
        "io.micrometer/micrometer-observation",
        "io.micrometer/micrometer-registry-prometheus",
        "io.projectreactor/reactor-core",
        "io.prometheus/prometheus-metrics-exposition-formats",
        "io.prometheus/prometheus-metrics-model",
        "jakarta.annotation/jakarta.annotation-api",
        "org.apache.httpcomponents.client5/httpclient5",
        "org.apache.tomcat.embed/tomcat-embed-core",
        "org.apache.tomcat.embed/tomcat-embed-websocket",
        "org.aspectj/aspectjweaver",
        "org.jetbrains.kotlin/kotlin-stdlib",
        "org.jspecify/jspecify",
        "org.reactivestreams/reactive-streams",
        "org.rocksdb/rocksdbjni",
        "org.slf4j/jul-to-slf4j",
        "tools.jackson.core/jackson-core",
        "tools.jackson.core/jackson-databind")),
    # SLF4J finds the logback provider through the service loader when it initializes; logback then
    # creates the appenders, encoders and models of its configuration for the LoggerContext.
    ("org.slf4j.LoggerFactory", (
        "ch.qos.logback/logback-classic",
        "org.slf4j/slf4j-api")),
    ("ch.qos.logback.classic.LoggerContext", (
        "ch.qos.logback/logback-core",)),
    # HikariConfig loads the configured driver class by name, and the first driver that registers
    # makes DriverManager load the others through the service loader.
    ("com.zaxxer.hikari.HikariConfig", (
        "com.mysql/mysql-connector-j",
        "org.apache.derby/derby",
        "org.apache.derby/derbyshared",
        "org.apache.derby/derbytools",
        "org.postgresql/postgresql",
        "org.xerial/sqlite-jdbc")),
    # JRaft creates its service factory and the gRPC implementation of its RPC factory through its
    # own service loader.
    ("com.alipay.sofa.jraft.util.JRaftServiceLoader", (
        "com.alipay.sofa/jraft-core",
        "com.alipay.sofa/rpc-grpc-impl")),
    ("com.caucho.hessian.io.SerializerFactory", (
        "com.caucho/hessian",)),
    ("com.google.protobuf.ExtensionRegistryLite", (
        "com.google.protobuf/protobuf-java",)),
    ("com.lmax.disruptor.dsl.ProducerType", (
        "com.lmax/disruptor",)),
    # gRPC finds its name resolvers, load balancers and transports through registries.
    ("io.grpc.NameResolverRegistry", (
        "io.grpc/grpc-core",)),
    ("io.grpc.LoadBalancerRegistry", (
        "io.grpc/grpc-util",)),
    ("io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder", (
        "io.grpc/grpc-netty-shaded",)),
    # The JDK reads the service file of dnsjava when it first resolves a name, early in the start.
    ("java.net.InetAddress", (
        "dnsjava/dnsjava",)),
    # See REASONED_CONDITIONS.
    ("com.google.common.util.concurrent.AbstractFuture", (
        "com.google.guava/guava",)),
    ("org.jctools.queues.atomic.BaseMpscLinkedAtomicArrayQueue", (
        "org.jctools/jctools-core",)),
)
SPRING_FAMILY = "org.springframework"

# Owners whose condition type test/scripts/native-condition-order.py cannot confirm, and why the
# type is early enough anyway. The script compares the load of a class with the initialization of
# the type, and not what a static initializer does. The classes that these entries name are
# package-private superclasses of the type, which look up their fields in static initializers; those
# only run as part of the initialization of the type, and GraalVM reaches a type right before that
# starts (the 'SuperType' example in its documentation of conditions).
REASONED_CONDITIONS = {
    "com.google.guava/guava":
        "AbstractFutureState, the class of the entries, is a package-private superclass of "
        "AbstractFuture, and no other class extends it",
    "org.jctools/jctools-core":
        "the Fields classes of the entries are package-private superclasses of "
        "BaseMpscLinkedAtomicArrayQueue",
}


def owner_condition(owner):
    """The type that guards the entries of an owner, or None if the owner has no row."""
    if owner in (NACOS_OWNER, JDK_OWNER):
        return SERVER_ENTRY_POINT
    if owner.startswith(SPRING_FAMILY):
        return SPRING_BOOT
    for condition, owners in CONDITION_GROUPS:
        if owner in owners:
            return condition
    return None

# Types that belong to the JDK and therefore never appear on the application classpath.
JDK_PREFIXES = ("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.w3c.", "org.xml.", "org.ietf.")

# POLICY: entries that are never shipped, with the reason. The agent records them because of how
# it observed the traced JVM, not because the Native executable needs them.
DROPPED_TYPE_PREFIXES = (
    ("com.sun.management.internal.", "JVM diagnostic commands are unavailable in Native Image"),
    ("jdk.management.jfr.", "JFR management is unavailable in Native Image"),
    ("jdk.internal.", "JDK internals are handled by the Native Image builder"),
    ("com.oracle.svm.", "Native Image builder internals"),
    ("org.graalvm.", "Native Image builder internals"),
    ("org.junit.", "test framework"),
    ("org.mockito.", "test framework"),
    ("mockit.", "test framework"),
    ("org.opentest4j.", "test framework"),
    ("org.testcontainers.", "test framework"),
)

# Classes created by Spring AOT while the Native build prepares the application contexts. Their
# hints are written by that same build, so a static copy would be redundant and would go stale.
DROPPED_TYPE_PATTERNS = (
    (re.compile(r"\$\$SpringCGLIB\$\$\d+"), "generated by Spring AOT during the build"),
    (re.compile(r"__[A-Z][A-Za-z]*$"), "generated by Spring AOT during the build"),
)

# GraalVM 25 documents that metadata cannot be provided for lambda-proxy classes, and probing a
# Native executable confirmed it: a ``lambda`` entry, with or without member flags, changes nothing
# that reflection on such a class finds. The agent records them nevertheless.
LAMBDA_REASON = "GraalVM cannot apply metadata to lambda-proxy classes"

# How a Native executable names a lambda class in its reports, for example
# ``p.Holder$$Lambda/0x1a2b`` or ``p.Holder$$Lambda.0x1a2b``.
LAMBDA_CLASS = re.compile(r"\$\$Lambda[./]0x[0-9a-f]+$")

# Strings that identify the machine or the person who ran the trace. A committed file containing
# any of them is rejected instead of silently rewritten.
FORBIDDEN_TEXT = (
    (re.compile(r"/home/|/Users/|/root/|C:\\\\|[A-Za-z]:/|file:/"), "absolute file system path"),
    (re.compile(r"\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"), "IP address"),
    (re.compile(r"localhost|127\.0\.0\.1"), "host name"),
)

JAVA_NAME = re.compile(r"^[A-Za-z_$][\w$]*(\.[A-Za-z_$][\w$]*)*$")
BOOLEAN_FLAGS = ("allDeclaredConstructors", "allPublicConstructors", "allDeclaredMethods",
                 "allPublicMethods", "allDeclaredFields", "allPublicFields", "unsafeAllocated",
                 "serializable", "jniAccessible", "queryAllDeclaredConstructors",
                 "queryAllPublicConstructors", "queryAllDeclaredMethods", "queryAllPublicMethods")


def jar_owner(path, names):
    """The ``groupId/artifactId`` that owns a jar, or ``unknown/<file name>``.

    The Maven descriptors inside the jar tell it (``META-INF/maven/<groupId>/<artifactId>/
    pom.properties``). A shaded jar carries those of the libraries it bundles as well, so the one
    whose artifact id starts the name of the file wins. Without descriptors the layout of a Maven
    repository decides (``<groupId as a path>/<artifactId>/<version>/<file>``). Whatever Nacos
    builds belongs to NACOS_OWNER, from whichever module it comes.
    """
    best = None
    for name in names:
        found = re.fullmatch(r"META-INF/maven/([^/]+)/([^/]+)/pom\.properties", name)
        if found and path.name.startswith(found.group(2) + "-") \
                and (best is None or len(found.group(2)) > len(best[1])):
            best = found.groups()
    parts = path.parts
    if best is None and len(parts) > 4 and "repository" in parts[:-3] \
            and parts[-1].startswith(parts[-3] + "-"):
        root = len(parts) - 1 - parts[::-1].index("repository")
        best = (".".join(parts[root + 1:-3]), parts[-3])
    if best is None:
        return "unknown/" + path.stem
    return NACOS_OWNER if best[0] == "com.alibaba.nacos" else "%s/%s" % best


def add_owner(owners, owner):
    if owner not in owners:
        owners.append(owner)


def entries_that(count, one, many):
    """``1 entry <one>`` or ``<count> entries <many>``, for the lines that report many entries."""
    return "1 entry " + one if count == 1 else "%d entries %s" % (count, many)


class Classpath:
    """Class and resource names available to the Native image builder, and who owns them."""

    def __init__(self, entries):
        self.classes = set()
        self.classes_as_paths = set()
        self.resources = set()
        self.directories = set()
        # The first entry of the classpath that has a class owns it, as for a class loader. A
        # resource and a directory are owned by every entry that has them, in the order of the
        # classpath.
        self.class_owner = {}
        self.resource_owners = {}
        self.directory_owners = {}
        for entry in entries:
            path = Path(entry)
            if path.is_dir():
                for root, _, files in os.walk(path):
                    for name in files:
                        self._add(os.path.relpath(os.path.join(root, name), path).replace(os.sep, "/"),
                                  NACOS_OWNER)
            elif path.is_file() and path.suffix in (".jar", ".zip"):
                with zipfile.ZipFile(path) as archive:
                    names = [name for name in archive.namelist() if not name.endswith("/")]
                owner = jar_owner(path, names)
                for name in names:
                    self._add(name, owner)

    def _add(self, name, owner):
        if name.endswith(".class"):
            name = name[:-len(".class")]
            self.classes.add(name.replace("/", "."))
            self.classes_as_paths.add(name)
            self.class_owner.setdefault(name.replace("/", "."), owner)
            return
        self.resources.add(name)
        add_owner(self.resource_owners.setdefault(name, []), owner)
        parent = name
        while "/" in parent:
            parent = parent.rsplit("/", 1)[0]
            self.directories.add(parent)
            add_owner(self.directory_owners.setdefault(parent, []), owner)

    @classmethod
    def from_file(cls, file):
        """Read either a plain path list or the ``-cp`` argument file of the Native build."""
        text = Path(file).read_text().strip()
        lines = text.splitlines()
        if lines and lines[0].strip() == "-cp":
            text = lines[1].strip()
            if text.startswith('"') and text.endswith('"'):
                text = text[1:-1].replace('\\"', '"').replace("\\\\", "\\")
        return cls([entry for entry in text.replace("\n", os.pathsep).split(os.pathsep) if entry])

    def has_type(self, name):
        name = base_type(name)
        if name in PRIMITIVES or name.startswith(JDK_PREFIXES):
            return True
        return name in self.classes

    def has_bundle(self, name):
        """A bundle exists if its base file or any locale variant (name_xx...) is present."""
        base = name.replace(".", "/")
        return any(entry == base or entry.startswith(base + "_") or entry.startswith(base + ".")
                   for entry in self.classes_as_paths | self.resources)

    def has_resource(self, glob):
        if "*" in glob:
            pattern = re.compile(re.escape(glob).replace(r"\*\*", ".*").replace(r"\*", "[^/]*") + "$")
            return any(pattern.match(name) for name in self.resources)
        return glob in self.resources or glob in self.directories

    def owner_of_type(self, name):
        """The owner of a class: its jar, JDK_OWNER for a JDK class or a primitive, else None."""
        name = base_type(name)
        if name in self.class_owner:
            return self.class_owner[name]
        if name in PRIMITIVES or name.startswith(JDK_PREFIXES):
            return JDK_OWNER
        return None

    def owners_of_resource(self, entry):
        """The owners of a resource or bundle entry, in the order of the classpath."""
        owners = []
        if "bundle" in entry:
            bundle = entry["bundle"]
            base = bundle.replace(".", "/")
            for name, found in self.resource_owners.items():
                if name == base or name.startswith(base + "_") or name.startswith(base + "."):
                    for owner in found:
                        add_owner(owners, owner)
            if not owners:
                for name, owner in self.class_owner.items():
                    if name == bundle or name.startswith(bundle + "_"):
                        add_owner(owners, owner)
            return owners
        glob = entry["glob"]
        if "*" in glob:
            pattern = re.compile(re.escape(glob).replace(r"\*\*", ".*").replace(r"\*", "[^/]*") + "$")
            for name, found in self.resource_owners.items():
                if pattern.match(name):
                    for owner in found:
                        add_owner(owners, owner)
            return owners
        return list(self.resource_owners.get(glob) or self.directory_owners.get(glob, []))


PRIMITIVES = ("boolean", "byte", "char", "short", "int", "long", "float", "double", "void")


def base_type(name):
    """Element type of an array name written as ``a.B[]`` or in JVM form ``[La.B;``."""
    while name.endswith("[]"):
        name = name[:-2]
    name = name.lstrip("[")
    if name.startswith("L") and name.endswith(";"):
        name = name[1:-1]
    return name


def type_key(entry):
    """Stable identity of a reflection entry: a class name, or a proxy/lambda description."""
    kind = entry["type"]
    if isinstance(kind, str):
        return kind
    return json.dumps(kind, sort_keys=True, separators=(",", ":"))


def condition_key(entry):
    return json.dumps(entry.get("condition"), sort_keys=True)


def guarded(entry, condition_type=SERVER_ENTRY_POINT):
    """A copy of the entry that the type guards, whatever condition the entry carries."""
    return dict(entry, condition={"typeReached": condition_type})


def apply_owner_conditions(document, classpath):
    """The document with the condition of the owner on every entry; raises for an owner without one.

    The condition is a function of the owner and nothing else, so that the inputs of a refresh (a
    trace, the report of an exact-mode build, the committed files) can be mixed freely.
    """
    lacking, result = set(), {}
    for section, owner_of in (("reflection", owner_of_reflection),
                              ("resources", owner_of_resource)):
        entries = []
        for entry in document.get(section, []):
            owner = owner_of(entry, classpath)
            condition = owner_condition(owner) if owner else SERVER_ENTRY_POINT
            if condition is None:
                lacking.add(owner)
                condition = SERVER_ENTRY_POINT
            entries.append(guarded(entry, condition))
        result[section] = entries
    if lacking:
        pronoun = "it" if len(lacking) == 1 else "them"
        raise SystemExit("No condition type for %s; add %s to CONDITION_GROUPS in %s after "
                         "comparing the type with the class log of a Server run (see "
                         "README-AOT.md, 'Conditions')" % (
                             ", ".join(sorted(lacking)), pronoun, Path(__file__).name))
    return result


def entry_point_problem(entry_point=SERVER_ENTRY_POINT):
    """Return why the Server entry point cannot guard the entries, or None.

    Renaming the class would leave the entries of Nacos and of the JDK guarded by a type that does
    not exist, and the metadata would silently stop taking effect. The tool runs from the root of
    the repository.
    """
    source = Path("bootstrap/src/main/java") / (entry_point.replace(".", "/") + ".java")
    if source.is_file():
        return None
    return ("%s guards the entries but has no source file %s; after a rename, update "
            "SERVER_ENTRY_POINT and the conditions of the bundled files" % (entry_point, source))


def condition_problem(entry, classpath):
    """Return what is wrong with the condition of a reflection or resource entry, or None.

    The text continues the sentence "N entries have ...", so that the problem of many entries is
    reported once instead of once per entry.
    """
    condition = entry.get("condition")
    if condition is None:
        return "no condition"
    guard = condition.get("typeReached") if isinstance(condition, dict) else None
    if not isinstance(guard, str) or set(condition) != {"typeReached"} \
            or not JAVA_NAME.match(guard):
        return "the invalid condition " + json.dumps(condition, sort_keys=True)
    if classpath is not None and not classpath.has_type(guard):
        return "the condition type %s, which is not on the Native classpath" % guard
    return None


def merge_reflection(entries):
    merged = {}
    for entry in entries:
        key = (type_key(entry), condition_key(entry))
        target = merged.setdefault(key, {})
        for name, value in entry.items():
            if name == "methods":
                known = {(m["name"], tuple(m.get("parameterTypes", []))): m
                         for m in target.get("methods", [])}
                for method in value:
                    known[(method["name"], tuple(method.get("parameterTypes", [])))] = method
                target["methods"] = list(known.values())
            elif name == "fields":
                known = {f["name"]: f for f in target.get("fields", [])}
                for field in value:
                    known[field["name"]] = field
                target["fields"] = list(known.values())
            elif name in BOOLEAN_FLAGS:
                target[name] = bool(target.get(name)) or bool(value)
            else:
                target[name] = value
    return list(merged.values())


def normalize(entry):
    entry = dict(entry)
    if "methods" in entry:
        entry["methods"] = sorted(
            ({"name": m["name"], "parameterTypes": list(m.get("parameterTypes", []))}
             for m in entry["methods"]), key=lambda m: (m["name"], m["parameterTypes"]))
    if "fields" in entry:
        entry["fields"] = sorted(({"name": f["name"]} for f in entry["fields"]),
                                 key=lambda f: f["name"])
    for flag in BOOLEAN_FLAGS:
        if flag in entry and not entry[flag]:
            del entry[flag]
    return entry


def reflection_sort_key(entry):
    kind = entry["type"]
    return (0, kind, condition_key(entry)) if isinstance(kind, str) \
        else (1, type_key(entry), condition_key(entry))


def resource_sort_key(entry):
    return ("bundle" in entry, entry.get("module", ""), entry.get("glob", entry.get("bundle", "")),
            condition_key(entry))


def policy_reason(name):
    """Return why a type name must never be shipped, or None."""
    for prefix, reason in DROPPED_TYPE_PREFIXES:
        if name.startswith(prefix):
            return reason
    for pattern, reason in DROPPED_TYPE_PATTERNS:
        if pattern.search(name):
            return reason
    return None


def drop_reason(entry, classpath):
    """Return why a reflection entry must not be shipped, or None to keep it.

    ``classpath`` may be None to apply only the classpath independent policy.
    """
    kind = entry["type"]
    if isinstance(kind, dict):
        if "lambda" in kind:
            return LAMBDA_REASON
        for name in kind.get("proxy", []):
            if policy_reason(name):
                return policy_reason(name)
            if classpath is not None and not classpath.has_type(name):
                return "type not on the Native classpath: " + name
        return None
    if policy_reason(kind):
        return policy_reason(kind)
    if classpath is not None and not classpath.has_type(kind):
        return "type not on the Native classpath"
    return None


def load_trace(location):
    """The documents of an input: a file, the output directory of the agent, or the whole bundle.

    A directory with a metadata file in it is an agent output. Otherwise all metadata files below
    it count, which is how the committed bundle, one file per owner, is read.
    """
    path = Path(location)
    if path.is_dir():
        files = [path / METADATA_NAME] if (path / METADATA_NAME).is_file() \
            else sorted(path.rglob(METADATA_NAME))
    else:
        files = [path] if path.is_file() else []
    if not files:
        raise SystemExit("No %s in %s" % (METADATA_NAME, location))
    return [json.loads(file.read_text()) for file in files]


def curate(traces, classpath):
    """Merge, filter and sort agent output. Returns the document and a list of drop reports."""
    reflection, resources, dropped = [], [], []
    for trace in traces:
        unknown = set(trace) - set(SECTIONS)
        if unknown:
            raise SystemExit("Unsupported metadata sections in trace: %s" % sorted(unknown))
        reflection.extend(guarded(entry) for entry in trace.get("reflection", []))
        resources.extend(guarded(entry) for entry in trace.get("resources", []))
    kept = []
    for entry in merge_reflection(reflection):
        reason = drop_reason(entry, classpath)
        if reason:
            dropped.append(("reflection", type_key(entry), reason))
        else:
            kept.append(normalize(entry))
    seen, kept_resources = set(), []
    for entry in resources:
        text = entry.get("glob", entry.get("bundle", ""))
        marker = json.dumps(entry, sort_keys=True)
        if marker in seen:
            continue
        seen.add(marker)
        if not text:
            dropped.append(("resources", "<empty>", "empty glob"))
        elif "glob" in entry and not classpath.has_resource(entry["glob"]):
            dropped.append(("resources", text, "resource not on the Native classpath"))
        elif "bundle" in entry and not entry.get("module") \
                and not classpath.has_bundle(entry["bundle"]):
            dropped.append(("resources", text, "bundle not on the Native classpath"))
        else:
            kept_resources.append(entry)
    return ({"reflection": sorted(kept, key=reflection_sort_key),
             "resources": sorted(kept_resources, key=resource_sort_key)}, dropped)


def render(document):
    """One entry per line, keys sorted: stable, reviewable and diff friendly.

    A section without entries is left out: most owners have no resources.
    """
    sections = [section for section in SECTIONS if document.get(section)]
    lines = ["{"]
    for index, section in enumerate(sections):
        entries = document[section]
        lines.append('  "%s": [' % section)
        for position, entry in enumerate(entries):
            comma = "," if position < len(entries) - 1 else ""
            lines.append("    " + json.dumps(entry, sort_keys=True, separators=(", ", ": ")) + comma)
        lines.append("  ]" + ("," if index < len(sections) - 1 else ""))
    lines.append("}")
    return "\n".join(lines) + "\n"


def owner_of_reflection(entry, classpath):
    """The owner of a reflection entry, or None if its class is on no classpath.

    A proxy has several interfaces, often of different owners. It belongs to Nacos if one of them
    does, since the proxy is then one of a Nacos bean; else to the owner of its first interface
    that is not a JDK type; else to the JDK.
    """
    kind = entry["type"]
    if isinstance(kind, str):
        return classpath.owner_of_type(kind)
    owners = [classpath.owner_of_type(name) for name in kind.get("proxy", [])]
    if None in owners:
        return None
    if NACOS_OWNER in owners:
        return NACOS_OWNER
    return next((owner for owner in owners if owner != JDK_OWNER), JDK_OWNER)


def owner_of_resource(entry, classpath):
    """The owner of a resource entry: Nacos if it has the resource, else the first that has it.

    Several jars can hold the same resource, for example a ``META-INF/services`` file; the entry is
    then written once, and ``check`` accepts it in the file of any of them. A bundle of a module
    is not looked up on the classpath and belongs to the JDK.
    """
    owners = classpath.owners_of_resource(entry)
    if not owners:
        return JDK_OWNER if entry.get("module") else None
    return NACOS_OWNER if NACOS_OWNER in owners else owners[0]


def partition(document, classpath):
    """Split a curated document by owner: ``{owner: document}``. Raises if an owner is unknown."""
    parts, unowned = {}, []
    for section, owner_of in (("reflection", owner_of_reflection),
                              ("resources", owner_of_resource)):
        for entry in document.get(section, []):
            owner = owner_of(entry, classpath)
            if owner is None or owner.startswith("unknown/"):
                unowned.append("%s (%s)" % (entry.get("type", entry.get("glob", entry.get("bundle"))),
                                            owner or "on no classpath entry"))
                continue
            parts.setdefault(owner, {name: [] for name in SECTIONS})[section].append(entry)
    if unowned:
        raise SystemExit("No Maven artifact owns %d entries; a jar without Maven descriptors that is "
                         "not in a Maven repository layout cannot be told apart, for example: %s"
                         % (len(unowned), ", ".join(unowned[:3])))
    return parts


def bundle_files(root):
    """``{owner: file}`` of the metadata files below root; the owner is the folder of the file."""
    return {file.parent.relative_to(root).as_posix(): file
            for file in sorted(Path(root).rglob(METADATA_NAME))}


def write_bundle(root, parts):
    """Write one file per owner below root and remove the files of owners that have none left."""
    root = Path(root)
    stale = bundle_files(root) if root.is_dir() else {}
    for owner, document in sorted(parts.items()):
        file = root / owner / METADATA_NAME
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(render(document))
    for owner, file in stale.items():
        if owner not in parts:
            file.unlink()
    for folder in sorted((path for path in root.rglob("*") if path.is_dir()), reverse=True):
        if not any(folder.iterdir()):
            folder.rmdir()


def problems(document, classpath=None):
    """Return a list of human readable problems in a metadata document."""
    found = []
    unknown = set(document) - set(SECTIONS)
    if unknown:
        found.append("unsupported sections: %s" % sorted(unknown))
    reflection = document.get("reflection", [])
    resources = document.get("resources", [])
    keys = [(type_key(entry), condition_key(entry)) for entry in reflection]
    if len(set(keys)) != len(keys):
        found.append("duplicate reflection entries")
    if reflection != sorted(reflection, key=reflection_sort_key):
        found.append("reflection entries are not sorted")
    if resources != sorted(resources, key=resource_sort_key):
        found.append("resources are not sorted")
    if len({json.dumps(entry, sort_keys=True) for entry in resources}) != len(resources):
        found.append("duplicate resource entries")
    conditions = {}
    for entry in reflection:
        kind = entry.get("type")
        if isinstance(kind, str):
            if not JAVA_NAME.match(base_type(kind)):
                found.append("invalid type name: %s" % kind)
        elif not (isinstance(kind, dict) and (set(kind) == {"proxy"} or set(kind) == {"lambda"})):
            found.append("invalid type: %s" % json.dumps(kind))
        reason = drop_reason(entry, classpath)
        if reason:
            found.append("%s must not be shipped: %s" % (type_key(entry), reason))
        issue = condition_problem(entry, classpath)
        if issue:
            conditions.setdefault(issue, []).append(type_key(entry))
    for entry in resources:
        text = entry.get("glob", entry.get("bundle", ""))
        if not text or text.endswith("/") or "//" in text:
            found.append("invalid resource: %s" % json.dumps(entry))
        if classpath is not None and "glob" in entry and not classpath.has_resource(entry["glob"]):
            found.append("resource not on the Native classpath: %s" % entry["glob"])
        issue = condition_problem(entry, classpath)
        if issue:
            conditions.setdefault(issue, []).append(text or json.dumps(entry, sort_keys=True))
    for issue, names in sorted(conditions.items()):
        found.append("%s, for example %s" % (
            entries_that(len(names), "has " + issue, "have " + issue), ", ".join(sorted(names)[:3])))
    text = json.dumps(document)
    for pattern, reason in FORBIDDEN_TEXT:
        match = pattern.search(text)
        if match:
            found.append("%s: %r" % (reason, match.group(0)))
    return found


def command_refresh(args):
    classpath = Classpath.from_file(args.classpath)
    traces = [document for location in args.trace for document in load_trace(location)]
    document, dropped = curate(traces, classpath)
    if not args.output:
        # A list for a reader (--output) must not fail because an owner has no row yet; the
        # bundle must not be written without one.
        document = apply_owner_conditions(document, classpath)
    issues = problems(document, classpath)
    for section, name, reason in sorted(dropped):
        print("dropped %-10s %s (%s)" % (section, name, reason), file=sys.stderr)
    if issues:
        for issue in issues:
            print("PROBLEM: " + issue, file=sys.stderr)
        return 1
    if args.output:
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(render(document))
        print("wrote %s: %d reflection entries, %d resources, %d dropped" % (
            output, len(document["reflection"]), len(document["resources"]), len(dropped)))
        return 0
    parts = partition(document, classpath)
    write_bundle(args.output_dir, parts)
    print("wrote %d files below %s: %d reflection entries, %d resources, %d dropped" % (
        len(parts), args.output_dir, len(document["reflection"]), len(document["resources"]),
        len(dropped)))
    return 0


def command_check(args):
    """Check one file, or all files below a directory: the bundle with one file per owner."""
    path = Path(args.file)
    classpath = Classpath.from_file(args.classpath) if args.classpath else None
    files = bundle_files(path) if path.is_dir() else {None: path} if path.is_file() else {}
    if not files:
        print("PROBLEM: no %s below %s" % (METADATA_NAME, path), file=sys.stderr)
        return 1
    issues, reflection_owner, resource_owner, repeated = [], {}, {}, []
    totals = [0, 0]
    for owner, file in files.items():
        prefix = "" if owner is None else owner + ": "
        text = file.read_text()
        try:
            document = json.loads(text)
        except ValueError as error:
            issues.append("%sis not valid JSON: %s" % (prefix, error))
            continue
        issues.extend(prefix + issue for issue in problems(document, classpath))
        if render(document) != text:
            issues.append(prefix + "file is not in canonical form; regenerate it with 'refresh' "
                                   "or reformat")
        reflection, resources = document.get("reflection", []), document.get("resources", [])
        totals[0] += len(reflection)
        totals[1] += len(resources)
        if owner is None:
            continue
        if not re.fullmatch(r"[^/]+/[^/]+", owner):
            issues.append("%sbelongs at <groupId>/<artifactId>/%s" % (prefix, METADATA_NAME))
        expected = owner_condition(owner)
        if expected is None:
            issues.append("%shas no condition type; add the owner to CONDITION_GROUPS" % prefix)
        else:
            unguarded = [type_key(entry) for entry in reflection
                         if entry.get("condition") != {"typeReached": expected}]
            unguarded += [entry.get("glob", entry.get("bundle")) for entry in resources
                          if entry.get("condition") != {"typeReached": expected}]
            if unguarded:
                issues.append("%s%s, for example %s" % (
                    prefix, entries_that(len(unguarded), "is not guarded by " + expected,
                                         "are not guarded by " + expected),
                    ", ".join(sorted(unguarded)[:3])))
        for key, entries, seen in (
                (lambda e: (type_key(e), condition_key(e)), reflection, reflection_owner),
                (lambda e: json.dumps(e, sort_keys=True), resources, resource_owner)):
            for entry in entries:
                if key(entry) in seen:
                    repeated.append("%s (in %s and %s)" % (
                        entry.get("type", entry.get("glob", entry.get("bundle"))),
                        seen[key(entry)], owner))
                seen.setdefault(key(entry), owner)
        if classpath is not None:
            misplaced = [type_key(entry) for entry in reflection
                         if owner_of_reflection(entry, classpath) not in (None, owner)]
            misplaced += [entry.get("glob", entry.get("bundle")) for entry in resources
                          if classpath.owners_of_resource(entry)
                          and owner not in classpath.owners_of_resource(entry)]
            if misplaced:
                issues.append("%s%s, for example %s" % (
                    prefix, entries_that(len(misplaced), "belongs to the file of another owner",
                                         "belong to the file of another owner"),
                    ", ".join(sorted(misplaced)[:3])))
    if repeated:
        issues.append("%s, for example %s" % (
            entries_that(len(repeated), "is in more than one file", "are in more than one file"),
            ", ".join(sorted(repeated)[:3])))
    if entry_point_problem():
        issues.append(entry_point_problem())
    for issue in issues:
        print("PROBLEM: " + issue, file=sys.stderr)
    if not issues:
        counted = "%d files, " % len(files) if path.is_dir() else ""
        print("%s OK: %s%d reflection entries, %d resources" % (path, counted, *totals))
    return 1 if issues else 0


MISSING_ERROR = re.compile(
    r"Missing(?P<kind>\w*)RegistrationError: (?P<what>[^\n]*?)(?:\. To allow this operation[^\n]*)?$",
    re.MULTILINE)
# The reports about an invoked method or constructor and about an accessed field print the member
# the way ``Executable.toString()`` and ``Field.toString()`` do: modifiers and the result or field
# type come first, then the qualified member, for example
# ``public void p.Plain.setCount(int,java.lang.String) throws java.io.IOException``.
MEMBER_PREFIX = r"(?:[^\s']+ )*"
EXECUTABLE_SUFFIX = r"\((?P<params>[^)]*)\)(?: throws [^']*)?'$"
MISSING_PATTERNS = (
    ("resource", re.compile(r"Cannot access resource at path '(?P<glob>[^']+)'$")),
    ("bundle", re.compile(r"Cannot access resource bundle with name '(?P<bundle>[^']+)'$")),
    ("lambda", re.compile(
        r"Cannot reflectively access the lambda-proxy class declared in '[^']+' "
        r"inheriting \[[^\]]*\]$")),
    ("method", re.compile(
        r"Cannot reflectively access method '(?P<type>[^#']+)#(?P<name>[^(']+)\((?P<params>[^)]*)\)'$")),
    ("method", re.compile(
        r"Cannot reflectively invoke method '" + MEMBER_PREFIX
        + r"(?P<type>[^\s'(]+)\.(?P<name>[^\s.'(]+)" + EXECUTABLE_SUFFIX)),
    ("constructor", re.compile(
        r"Cannot reflectively invoke constructor '" + MEMBER_PREFIX
        + r"(?P<type>[^\s'(]+)" + EXECUTABLE_SUFFIX)),
    ("field", re.compile(r"Cannot reflectively access field '(?P<type>[^#']+)#(?P<name>[^']+)'$")),
    ("field", re.compile(
        r"Cannot reflectively read or write field '" + MEMBER_PREFIX
        + r"(?P<type>[^\s'(]+)\.(?P<name>[^\s.'(]+)'$")),
    ("unsafe", re.compile(r"Cannot reflectively unsafe instantiate '(?P<type>[^']+)'$")),
    ("serializable", re.compile(
        r"Cannot serialize or deserialize (?:type )?'(?P<type>[^']+)'(?: with target constructor.*)?$")),
    ("type", re.compile(r"Cannot reflectively access (?:the class |the )?'(?P<type>[^'#]+)'$")),
)


def parse_missing(text):
    """Turn missing registration reports into agent style metadata.

    The member is taken from the report's headline, not from the JSON snippet that follows it,
    because the snippet of a lookup only names the type. Reports about lambda-proxy classes are
    counted and skipped, because GraalVM cannot take metadata for them. Returns (document,
    unparsed headlines, number of lambda reports skipped).
    """
    reflection, resources, unparsed, lambdas = {}, [], set(), 0
    for report in MISSING_ERROR.finditer(text):
        what = report.group("what")
        for kind, pattern in MISSING_PATTERNS:
            found = pattern.match(what)
            if not found:
                continue
            if kind == "lambda" or LAMBDA_CLASS.search(found.groupdict().get("type") or ""):
                lambdas += 1
            elif kind == "resource":
                resources.append({"glob": found.group("glob")})
            elif kind == "bundle":
                resources.append({"bundle": found.group("bundle")})
            else:
                entry = reflection.setdefault(found.group("type"), {"type": found.group("type")})
                if kind in ("method", "constructor"):
                    params = [p.strip() for p in found.group("params").split(",") if p.strip()]
                    entry.setdefault("methods", []).append(
                        {"name": found.groupdict().get("name") or "<init>",
                         "parameterTypes": params})
                elif kind == "field":
                    entry.setdefault("fields", []).append({"name": found.group("name")})
                elif kind == "serializable":
                    entry["serializable"] = True
                elif kind == "unsafe":
                    entry["unsafeAllocated"] = True
            break
        else:
            unparsed.add(report.group("kind") + ": " + what)
    return {"reflection": merge_reflection(list(reflection.values())),
            "resources": resources}, sorted(unparsed), lambdas


def command_collect_missing(args):
    text = "\n".join(Path(log).read_text(errors="replace") for log in args.log)
    document, unparsed, lambdas = parse_missing(text)
    document = {"reflection": [normalize(e) for e in document["reflection"]],
                "resources": sorted({json.dumps(r, sort_keys=True): r
                                     for r in document["resources"]}.values(), key=resource_sort_key)}
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    (output / METADATA_NAME).write_text(json.dumps(document, indent=1, sort_keys=True) + "\n")
    print("collected %d reflection entries and %d resources from %d report(s); %d unparsed, "
          "%d about lambda-proxy classes skipped" % (
              len(document["reflection"]), len(document["resources"]),
              len(MISSING_ERROR.findall(text)), len(unparsed), lambdas))
    for headline in unparsed:
        print("UNPARSED " + headline, file=sys.stderr)
    return 0


def command_selftest(args):
    """Exercise merge, policy, canonical rendering and validation on synthetic data."""
    import tempfile
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        for name in ("sample/Model.class", "sample/Generated__BeanDefinitions.class",
                     "sample/Local.class", "sample/Local$Inner.class", "sample/Dep.class",
                     "sample/messages_de.properties", "sample/data.json", "META-INF/services/x.Spi",
                     SERVER_ENTRY_POINT.replace(".", "/") + ".class"):
            target = root / "classes" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text("")
        classpath = Classpath([str(root / "classes")])
        first = {"reflection": [
            {"type": "sample.Model", "methods": [{"name": "setA", "parameterTypes": ["int"]}]},
            {"type": "sample.Generated__BeanDefinitions"},
            {"type": "sample.Absent"},
            {"type": "com.sun.management.internal.HotSpotDiagnostic"},
            {"type": "java.lang.String[]"},
            {"type": {"proxy": ["sample.Dep", "java.io.Serializable"]}},
            {"type": {"lambda": {"declaringClass": "sample.Local", "interfaces": ["sample.Dep"]}}}],
            "resources": [{"glob": ""}, {"glob": "sample/data.json"}, {"glob": "sample/missing.json"},
                          {"glob": "META-INF/services/x.Spi"}, {"bundle": "sample.messages"},
                          {"bundle": "sample.nothing"}]}
        second = {"reflection": [
            {"type": "sample.Model", "fields": [{"name": "b"}], "allDeclaredMethods": True,
             "methods": [{"name": "setA", "parameterTypes": ["int"]},
                         {"name": "getA", "parameterTypes": []}]}],
            "resources": [{"glob": "sample/data.json"}]}
        document, dropped = curate([first, second], classpath)
        model = [e for e in document["reflection"] if e["type"] == "sample.Model"]
        assert len(model) == 1 and model[0]["allDeclaredMethods"] is True
        assert [m["name"] for m in model[0]["methods"]] == ["getA", "setA"]
        assert model[0]["fields"] == [{"name": "b"}]
        kept = [e["type"] if isinstance(e["type"], str) else "proxy" for e in document["reflection"]]
        assert kept == ["java.lang.String[]", "sample.Model", "proxy"], kept
        assert any(reason == LAMBDA_REASON for _, _, reason in dropped), dropped
        lambda_entry = {"type": {"lambda": {"declaringClass": "sample.Local",
                                            "interfaces": ["sample.Dep"]}}}
        assert any(LAMBDA_REASON in issue for issue in
                   problems({"reflection": [lambda_entry], "resources": []}, classpath))
        assert {name for _, name, _ in dropped} >= {
            "sample.Generated__BeanDefinitions", "sample.Absent",
            "com.sun.management.internal.HotSpotDiagnostic", "<empty>", "sample/missing.json",
            "sample.nothing"}
        assert [r.get("glob", r.get("bundle")) for r in document["resources"]] == [
            "META-INF/services/x.Spi", "sample/data.json", "sample.messages"]
        assert problems(document, classpath) == []
        # Every entry leaves 'curate' guarded by the Server entry point.
        guard = {"typeReached": SERVER_ENTRY_POINT}
        assert all(e["condition"] == guard for e in document["reflection"] + document["resources"])
        # A condition that an input carries does not count: the owner decides, so that the inputs
        # of a refresh can be mixed, and one type never stays under two conditions.
        foreign = {"reflection": [{"type": "sample.Model", "fields": [{"name": "c"}],
                                   "condition": {"typeReached": "sample.Local"}}],
                   "resources": [{"glob": "sample/data.json",
                                  "condition": {"typeReached": "sample.Local"}}]}
        mixed, _ = curate([first, second, foreign], classpath)
        assert [e["condition"] for e in mixed["reflection"]] == [guard] * len(mixed["reflection"])
        assert [e["condition"] for e in mixed["resources"]] == [guard] * len(mixed["resources"])
        assert len([e for e in mixed["reflection"] if e["type"] == "sample.Model"]) == 1
        assert problems(mixed, classpath) == [], problems(mixed, classpath)
        # The class that guards the entries exists in the repository; a vanished one is reported.
        assert entry_point_problem() is None, entry_point_problem()
        assert "has no source file" in entry_point_problem("sample.Gone")
        # An entry without a condition, with a malformed one or with a dangling one is reported.
        bare = {"reflection": [{"type": "sample.Model"}, {"type": "sample.Dep"}],
                "resources": [{"glob": "sample/data.json"}]}
        assert any("3 entries have no condition" in issue for issue in problems(bare, classpath))
        one = problems({"reflection": [{"type": "sample.Model"}], "resources": []}, classpath)
        assert any("1 entry has no condition" in issue for issue in one), one
        for condition, expected in (({"typeReached": 3}, "invalid condition"),
                                    ({"typeReached": "sample.Model[]"}, "invalid condition"),
                                    ({"typeReached": "sample.Model", "other": 1}, "invalid condition"),
                                    ("sample.Model", "invalid condition"),
                                    ({"typeReached": "sample.Absent"}, "not on the Native classpath")):
            broken = {"reflection": [{"type": "sample.Model", "condition": condition}],
                      "resources": []}
            assert any(expected in issue for issue in problems(broken, classpath)), condition
        text = render(document)
        assert json.loads(text) == document and render(json.loads(text)) == text
        target = root / METADATA_NAME
        target.write_text(text)
        assert command_check(argparse.Namespace(file=str(target), classpath=None)) == 0
        without_conditions = render({
            section: [{name: value for name, value in entry.items() if name != "condition"}
                      for entry in document[section]] for section in SECTIONS})
        for broken, expected in (
                (text.replace("sample.Model", "/home/someone/Model"), "absolute file system path"),
                (text.replace('"type": "java.lang.String[]"}', '"type": "zzz.Last"}', 1),
                 "not sorted"),
                (without_conditions, "have no condition")):
            target.write_text(broken)
            captured = io.StringIO()
            with contextlib.redirect_stderr(captured):
                assert command_check(argparse.Namespace(file=str(target), classpath=None)) == 1
            assert expected in captured.getvalue(), (expected, captured.getvalue())
    sample = (
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "method 'p.Plain#hello(java.lang.String, int)'. To allow this operation, add the following "
        "to the 'reflection' section of 'reachability-metadata.json' and rebuild the native image:\n"
        "\n  {\n    \"type\": \"p.Plain\"\n  }\n\n"
        "Caused by: org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot "
        "reflectively access method 'p.Plain#<init>()'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "field 'p.Plain#name'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "the class 'p.Other'. To allow this operation, add\n"
        "com.oracle.svm.core.reflect.serialize.MissingSerializationRegistrationError: Cannot "
        "serialize or deserialize type 'p.Data' with target constructor class 'java.lang.Object'. "
        "To allow this operation, add\n"
        "com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError: Cannot access "
        "resource at path 'a/b.txt'. To allow this operation, add\n"
        "com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError: Cannot access "
        "resource at path 'a/b.txt'. To allow this operation, add\n"
        "com.oracle.svm.core.jdk.resources.MissingResourceRegistrationError: Cannot access "
        "resource bundle with name 'p.Bundle'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "the lambda-proxy class declared in 'p.Holder' inheriting ['p.Api']. To allow this "
        "operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "method 'p.Holder$$Lambda/0x1a2b#run()'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively access "
        "the class 'p.Holder$$Lambda.0x1a2b'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingJNIRegistrationError: Something unexpected. To allow "
        "this operation, add\n")
    collected, unparsed, lambdas = parse_missing(sample)
    by_type = {e["type"]: normalize(e) for e in collected["reflection"]}
    assert lambdas == 3 and not [t for t in by_type if "Lambda" in t or "Holder" in t], by_type
    assert by_type["p.Plain"]["methods"] == [
        {"name": "<init>", "parameterTypes": []},
        {"name": "hello", "parameterTypes": ["java.lang.String", "int"]}], by_type["p.Plain"]
    assert by_type["p.Plain"]["fields"] == [{"name": "name"}]
    assert by_type["p.Other"] == {"type": "p.Other"}
    assert by_type["p.Data"] == {"type": "p.Data", "serializable": True}
    assert len(collected["resources"]) == 3, collected["resources"]
    assert unparsed == ["JNI: Something unexpected"], unparsed
    # The reports about invoked members, accessed fields and unsafe allocation name the member the
    # way Executable.toString() does, and a repeated report must not repeat the entry.
    invoked = (
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke "
        "method 'public void p.Bean.setCount(int,java.lang.String)'. To allow this operation, add\n"
        "Exception in thread \"worker-1\" org.graalvm.nativeimage.MissingReflectionRegistrationError: "
        "Cannot reflectively invoke method 'public java.lang.String p.Bean$Part.getName() throws "
        "java.io.IOException'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke "
        "method 'public void p.Bean.setCount(int,java.lang.String)'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke "
        "constructor 'public p.Bean(java.lang.String[],int)'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively read or "
        "write field 'private final java.util.List p.Bean.items'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively unsafe "
        "instantiate 'p.Bean'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively invoke "
        "method 'public void p.Holder$$Lambda/0x1a2b.run()'. To allow this operation, add\n"
        "org.graalvm.nativeimage.MissingReflectionRegistrationError: Cannot reflectively unknown "
        "'p.Bean'. To allow this operation, add\n")
    collected, unparsed, lambdas = parse_missing(invoked)
    by_type = {e["type"]: normalize(e) for e in collected["reflection"]}
    assert sorted(by_type) == ["p.Bean", "p.Bean$Part"] and lambdas == 1, (by_type, lambdas)
    assert by_type["p.Bean"] == {
        "type": "p.Bean", "unsafeAllocated": True, "fields": [{"name": "items"}],
        "methods": [{"name": "<init>", "parameterTypes": ["java.lang.String[]", "int"]},
                    {"name": "setCount", "parameterTypes": ["int", "java.lang.String"]}]}, by_type
    assert by_type["p.Bean$Part"]["methods"] == [{"name": "getName", "parameterTypes": []}]
    assert unparsed == ["Reflection: Cannot reflectively unknown 'p.Bean'"], unparsed
    # The owners of the sample jars get a condition type that exists on the sample classpath.
    with condition_groups((("a.A", ("org.example/lib-a",)), ("b.B", ("org.example/lib-b",)),
                           ("c.C", ("org.example/lib-c",)))):
        selftest_owners()
    selftest_condition_table()
    print("native-metadata selftest OK")
    return 0


@contextlib.contextmanager
def condition_groups(extra):
    """Add rows to CONDITION_GROUPS for the duration of a test."""
    global CONDITION_GROUPS
    saved = CONDITION_GROUPS
    CONDITION_GROUPS = tuple(extra) + saved
    try:
        yield
    finally:
        CONDITION_GROUPS = saved


def selftest_condition_table():
    """The table of condition types: well formed, one row per owner, none for Nacos or the JDK."""
    owners = [owner for _, group in CONDITION_GROUPS for owner in group]
    assert len(owners) == len(set(owners)), "an owner has two rows"
    assert all(re.fullmatch(r"[^/]+/[^/]+", owner) for owner in owners), owners
    assert not {NACOS_OWNER, JDK_OWNER} & set(owners)
    assert not [owner for owner in owners if owner.startswith(SPRING_FAMILY)], \
        "the Spring family has a rule of its own"
    assert all(JAVA_NAME.match(condition) for condition, _ in CONDITION_GROUPS)
    assert owner_condition(NACOS_OWNER) == owner_condition(JDK_OWNER) == SERVER_ENTRY_POINT
    assert owner_condition("org.springframework.boot/spring-boot-new") == SPRING_BOOT
    assert owner_condition("org.postgresql/postgresql") == "com.zaxxer.hikari.HikariConfig"
    assert owner_condition("org.example/unknown") is None


def selftest_owners():
    """Ownership, the layout with one file per owner and the checks that guard it."""
    import tempfile

    def jar(file, members):
        file.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(file, "w") as archive:
            for member in members:
                archive.writestr(member, "")
        return str(file)

    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        own_classes = root / "own-classes"
        for name in ("own/Own.class", SERVER_ENTRY_POINT.replace(".", "/") + ".class"):
            (own_classes / name).parent.mkdir(parents=True, exist_ok=True)
            (own_classes / name).write_text("")
        entries = [
            own_classes,
            # The descriptors of the jar name its owner.
            jar(root / "libs/lib-a-1.0.jar", [
                "META-INF/maven/org.example/lib-a/pom.properties", "a/A.class", "a/messages.properties",
                "a/data/one.json", "META-INF/services/x.Spi"]),
            # Without descriptors, the layout of a Maven repository names the owner.
            jar(root / "repository/org/example/lib-b/2.0/lib-b-2.0.jar", [
                "b/B.class", "b/Bundle.class", "META-INF/services/x.Spi"]),
            # A shaded jar carries the descriptors of what it bundles; its own wins.
            jar(root / "libs/lib-c-3.0.jar", [
                "META-INF/maven/org.example/lib-c/pom.properties",
                "META-INF/maven/org.inner/other/pom.properties", "c/C.class"]),
            # Every module of Nacos belongs to the same owner.
            jar(root / "libs/nacos-api-3.3.0.jar", [
                "META-INF/maven/com.alibaba.nacos/nacos-api/pom.properties",
                "com/alibaba/nacos/api/N.class", "META-INF/services/y.Spi"]),
            # Another jar with the same class: the first on the classpath owns it.
            jar(root / "libs/lib-d-4.0.jar", [
                "META-INF/maven/org.example/lib-d/pom.properties", "a/A.class", "d/D.class",
                "META-INF/services/y.Spi"]),
            jar(root / "libs/stray.jar", ["z/Z.class"]),
        ]
        classpath = Classpath([str(entry) for entry in entries])
        assert classpath.owner_of_type("own.Own") == NACOS_OWNER
        assert classpath.owner_of_type("a.A") == "org.example/lib-a"
        assert classpath.owner_of_type("b.B[]") == "org.example/lib-b"
        assert classpath.owner_of_type("c.C") == "org.example/lib-c"
        assert classpath.owner_of_type("com.alibaba.nacos.api.N") == NACOS_OWNER
        assert classpath.owner_of_type("java.lang.String") == classpath.owner_of_type("int") == JDK_OWNER
        assert classpath.owner_of_type("z.Z") == "unknown/stray" and classpath.owner_of_type("q.Q") is None
        assert classpath.owners_of_resource({"glob": "META-INF/services/x.Spi"}) == [
            "org.example/lib-a", "org.example/lib-b"]
        assert classpath.owners_of_resource({"glob": "a/data"}) == ["org.example/lib-a"]
        assert classpath.owners_of_resource({"glob": "a/**/*.json"}) == ["org.example/lib-a"]
        assert classpath.owners_of_resource({"bundle": "a.messages"}) == ["org.example/lib-a"]
        assert classpath.owners_of_resource({"bundle": "b.Bundle"}) == ["org.example/lib-b"]
        assert classpath.owners_of_resource({"glob": "nothing"}) == []

        def proxy(*names):
            return {"type": {"proxy": list(names)}}

        assert owner_of_reflection({"type": "a.A"}, classpath) == "org.example/lib-a"
        assert owner_of_reflection(proxy("java.io.Serializable", "c.C", "a.A"), classpath) \
            == "org.example/lib-c"
        assert owner_of_reflection(proxy("b.B", "own.Own"), classpath) == NACOS_OWNER
        assert owner_of_reflection(proxy("java.io.Serializable"), classpath) == JDK_OWNER
        assert owner_of_reflection(proxy("b.B", "q.Q"), classpath) is None
        assert owner_of_resource({"glob": "META-INF/services/y.Spi"}, classpath) == NACOS_OWNER
        assert owner_of_resource({"glob": "META-INF/services/x.Spi"}, classpath) == "org.example/lib-a"

        trace = {"reflection": [{"type": "a.A"}, {"type": "b.B"}, {"type": "own.Own"},
                                {"type": "java.lang.String"}, proxy("c.C", "java.io.Serializable")],
                 "resources": [{"glob": "META-INF/services/x.Spi"}, {"bundle": "b.Bundle"},
                               {"glob": "META-INF/services/y.Spi"}]}
        document, dropped = curate([trace], classpath)
        assert not dropped, dropped
        # The condition of an entry is the one of its owner; an owner without a row stops a refresh.
        document = apply_owner_conditions(document, classpath)
        parts = partition(document, classpath)

        def conditions(owner):
            return {e["condition"]["typeReached"] for e in parts[owner]["reflection"]}

        assert conditions("org.example/lib-a") == {"a.A"}
        assert conditions("org.example/lib-b") == {"b.B"}
        assert conditions("org.example/lib-c") == {"c.C"}
        assert conditions(NACOS_OWNER) == conditions(JDK_OWNER) == {SERVER_ENTRY_POINT}
        try:
            unowned, _ = curate([{"reflection": [{"type": "d.D"}]}], classpath)
            apply_owner_conditions(unowned, classpath)
            raise AssertionError("an owner without a condition type must stop the refresh")
        except SystemExit as refused:
            assert "No condition type for org.example/lib-d" in str(refused), refused
        assert sorted(parts) == sorted([JDK_OWNER, NACOS_OWNER, "org.example/lib-a",
                                        "org.example/lib-b", "org.example/lib-c"]), sorted(parts)
        assert [e["type"] for e in parts["org.example/lib-b"]["reflection"]] == ["b.B"]
        assert [e["bundle"] for e in parts["org.example/lib-b"]["resources"]] == ["b.Bundle"]
        assert [e["glob"] for e in parts[NACOS_OWNER]["resources"]] == ["META-INF/services/y.Spi"]
        try:
            partition(curate([{"reflection": [{"type": "z.Z"}]}], classpath)[0], classpath)
            raise AssertionError("an entry of a jar without coordinates must be refused")
        except SystemExit as refused:
            assert "No Maven artifact owns 1 entries" in str(refused), refused

        # One file per owner: an owner without a section writes none, a vanished owner loses its file
        # and the folders that stay empty.
        bundle = root / "bundle"
        write_bundle(bundle, parts)
        assert sorted(bundle_files(bundle)) == sorted(parts)
        assert '"resources"' not in (bundle / "org.example/lib-c" / METADATA_NAME).read_text()
        classpath_file = root / "classpath.txt"
        classpath_file.write_text("\n".join(str(entry) for entry in entries))

        def check(location, classpath_argument=None):
            captured = io.StringIO()
            with contextlib.redirect_stderr(captured), contextlib.redirect_stdout(io.StringIO()):
                code = command_check(argparse.Namespace(file=str(location), classpath=classpath_argument))
            return code, captured.getvalue()

        assert check(bundle) == (0, "")
        assert check(bundle, str(classpath_file)) == (0, "")
        assert len(load_trace(bundle)) == len(parts)
        # A condition other than the one of the owner is reported, also the one of the Server.
        lib_c = bundle / "org.example/lib-c" / METADATA_NAME
        original = lib_c.read_text()
        lib_c.write_text(original.replace('"c.C"', '"%s"' % SERVER_ENTRY_POINT))
        code, errors = check(bundle)
        assert code == 1 and "org.example/lib-c: 1 entry is not guarded by c.C" in errors, errors
        lib_c.write_text(original)
        assert check(bundle) == (0, "")
        with contextlib.redirect_stdout(io.StringIO()):
            assert command_refresh(argparse.Namespace(
                trace=[str(bundle)], classpath=str(classpath_file), output=None,
                output_dir=str(root / "again"))) == 0
        assert sorted(bundle_files(root / "again")) == sorted(parts)
        assert all((root / "again" / owner / METADATA_NAME).read_text()
                   == (bundle / owner / METADATA_NAME).read_text() for owner in parts)
        # An entry in the file of another owner, an entry in two files, a file at the wrong depth.
        lib_a = bundle / "org.example/lib-a" / METADATA_NAME
        lib_b = bundle / "org.example/lib-b" / METADATA_NAME
        moved = json.loads(lib_a.read_text())
        moved["reflection"].append(json.loads(lib_b.read_text())["reflection"][0])
        moved["reflection"].sort(key=reflection_sort_key)
        lib_a.write_text(render(moved))
        code, errors = check(bundle)
        assert code == 1 and "1 entry is in more than one file" in errors, errors
        code, errors = check(bundle, str(classpath_file))
        assert code == 1 and "1 entry belongs to the file of another owner" in errors, errors
        lib_b.unlink()
        code, errors = check(bundle, str(classpath_file))
        assert code == 1 and "org.example/lib-a: 1 entry belongs to the file of another owner" in errors
        (bundle / METADATA_NAME).write_text(render({"reflection": [], "resources": []}))
        assert "belongs at <groupId>/<artifactId>" in check(bundle)[1]
        (bundle / METADATA_NAME).unlink()
        broken = bundle / "org.example/lib-c" / METADATA_NAME
        broken.write_text("{")
        assert "is not valid JSON" in check(bundle)[1]
        assert check(root / "nothing-here")[0] == 1
        # A refresh without an owner's entries removes its file and the folders left empty.
        only_own = {"reflection": [{"type": "own.Own"}], "resources": []}
        write_bundle(bundle, partition(
            apply_owner_conditions(curate([only_own], classpath)[0], classpath), classpath))
        assert sorted(bundle_files(bundle)) == [NACOS_OWNER]
        assert not (bundle / "org.example").exists() and not (bundle / "jdk").exists()


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    refresh = commands.add_parser("refresh", help="curate agent output into the bundled files")
    refresh.add_argument("trace", nargs="+",
                         help="agent output directories, files and the committed bundle to merge")
    refresh.add_argument("--classpath", required=True,
                         help="Native classpath: bootstrap/target/native/classpath.args or a path list")
    target = refresh.add_mutually_exclusive_group()
    target.add_argument("--output-dir", default=DEFAULT_BUNDLE,
                        help="write one file per owner below this directory (default: the bundle)")
    target.add_argument("--output", help="write one merged file instead")
    refresh.set_defaults(handler=command_refresh)
    check = commands.add_parser("check", help="verify the committed metadata files")
    check.add_argument("file", nargs="?", default=DEFAULT_BUNDLE,
                       help="the bundle directory (default) or a single metadata file")
    check.add_argument("--classpath", help="also verify entries against this Native classpath")
    check.set_defaults(handler=command_check)
    missing = commands.add_parser(
        "collect-missing", help="turn missing registration reports of a Native run into metadata")
    missing.add_argument("log", nargs="+", help="log files of a run with MissingRegistrationReportingMode=Warn")
    missing.add_argument("--output", required=True, help="directory that receives reachability-metadata.json")
    missing.set_defaults(handler=command_collect_missing)
    selftest = commands.add_parser("selftest", help="run built-in checks of this tool")
    selftest.set_defaults(handler=command_selftest)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    os.chdir(root)
    return args.handler(args)


if __name__ == "__main__":
    sys.exit(main())
