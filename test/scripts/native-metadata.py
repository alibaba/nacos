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
``reachability-metadata.json``. It merges several agent runs, drops entries that must not be
shipped (see POLICY below), prunes classes and resources that are not on the Native classpath,
and writes the result in a stable, line-oriented order so that reviews and diffs stay small.

``check`` verifies a committed file without needing GraalVM or a database. CI runs it from
``native-build-it.py``.

Every entry carries a ``typeReached`` condition (see SERVER_ENTRY_POINT). ``refresh`` adds the
default condition to each input entry that has none and keeps a condition that is present;
``check`` rejects an entry without a well-formed condition.

``collect-missing`` closes the loop from the other side: an executable built with
``--exact-reachability-metadata`` and started with ``-XX:MissingRegistrationReportingMode=Warn``
prints every dynamic access that has no metadata. This command turns such logs into the same
format as the agent output, so ``refresh`` can merge them. It reads the reports about classes,
methods and constructors that were looked up or invoked, fields, unsafe allocation,
serialization, resources and resource bundles. Reports about lambda-proxy classes are counted
and skipped. A report it does not understand is printed as ``UNPARSED``, and the ``exact``
variant of the Native CI job fails on it instead of ignoring a gap that it cannot name.

Typical refresh, after tracing the Server with the exact classpath of the Native build::

    python3 test/scripts/native-metadata.py refresh \\
        --classpath bootstrap/target/native/classpath.args \\
        --output bootstrap/src/main/resources/META-INF/native-image/com.alibaba.nacos/nacos-bootstrap/reachability-metadata.json \\
        /path/to/trace-run-1 /path/to/trace-run-2
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

DEFAULT_METADATA = ("bootstrap/src/main/resources/META-INF/native-image/"
                    "com.alibaba.nacos/nacos-bootstrap/reachability-metadata.json")
METADATA_NAME = "reachability-metadata.json"
SECTIONS = ("reflection", "resources")

# GraalVM recommends that every entry be conditional. An entry with a typeReached condition is
# included in the image if the type is reachable at build time, and it is available at run time
# once the type is reached, that is, right before the initialization of its class starts; until
# then every dynamic access that the entry serves behaves as if the entry did not exist. The entry
# point of the Server is initialized before any other Server code runs, so guarding every entry
# with it keeps all of them available to every thread and keeps the file out of an image that does
# not contain the Server. Narrow a condition only to a type that is certain to be initialized
# before the first access that the entry serves: a class that is only named by a class literal is
# never initialized.
SERVER_ENTRY_POINT = "com.alibaba.nacos.bootstrap.NacosBootstrap"

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


class Classpath:
    """Class and resource names available to the Native image builder."""

    def __init__(self, entries):
        self.classes = set()
        self.classes_as_paths = set()
        self.resources = set()
        self.directories = set()
        for entry in entries:
            path = Path(entry)
            if path.is_dir():
                for root, _, files in os.walk(path):
                    for name in files:
                        self._add(os.path.relpath(os.path.join(root, name), path).replace(os.sep, "/"))
            elif path.is_file() and path.suffix in (".jar", ".zip"):
                with zipfile.ZipFile(path) as archive:
                    for name in archive.namelist():
                        if not name.endswith("/"):
                            self._add(name)

    def _add(self, name):
        if name.endswith(".class"):
            self.classes.add(name[:-len(".class")].replace("/", "."))
            self.classes_as_paths.add(name[:-len(".class")])
            return
        self.resources.add(name)
        parent = name
        while "/" in parent:
            parent = parent.rsplit("/", 1)[0]
            self.directories.add(parent)

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


def guarded(entry):
    """The entry itself if it has a condition, else a copy that the Server entry point guards."""
    if "condition" in entry:
        return entry
    return dict(entry, condition={"typeReached": SERVER_ENTRY_POINT})


def entry_point_problem(entry_point=SERVER_ENTRY_POINT):
    """Return why the Server entry point cannot guard the entries, or None.

    Renaming the class would leave every entry guarded by a type that does not exist, and the
    metadata would silently stop taking effect. The tool runs from the root of the repository.
    """
    source = Path("bootstrap/src/main/java") / (entry_point.replace(".", "/") + ".java")
    if source.is_file():
        return None
    return ("%s guards the entries but has no source file %s; after a rename, update "
            "SERVER_ENTRY_POINT and the conditions of the bundled file" % (entry_point, source))


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
    path = Path(location)
    if path.is_dir():
        path = path / METADATA_NAME
    if not path.is_file():
        raise SystemExit("No %s in %s" % (METADATA_NAME, location))
    return json.loads(path.read_text())


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
    """One entry per line, keys sorted: stable, reviewable and diff friendly."""
    lines = ["{"]
    for index, section in enumerate(SECTIONS):
        entries = document.get(section, [])
        lines.append('  "%s": [' % section)
        for position, entry in enumerate(entries):
            comma = "," if position < len(entries) - 1 else ""
            lines.append("    " + json.dumps(entry, sort_keys=True, separators=(", ", ": ")) + comma)
        lines.append("  ]" + ("," if index < len(SECTIONS) - 1 else ""))
    lines.append("}")
    return "\n".join(lines) + "\n"


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
        count = "1 entry has" if len(names) == 1 else "%d entries have" % len(names)
        found.append("%s %s, for example %s" % (count, issue, ", ".join(sorted(names)[:3])))
    text = json.dumps(document)
    for pattern, reason in FORBIDDEN_TEXT:
        match = pattern.search(text)
        if match:
            found.append("%s: %r" % (reason, match.group(0)))
    return found


def command_refresh(args):
    classpath = Classpath.from_file(args.classpath)
    document, dropped = curate([load_trace(trace) for trace in args.trace], classpath)
    issues = problems(document, classpath)
    for section, name, reason in sorted(dropped):
        print("dropped %-10s %s (%s)" % (section, name, reason), file=sys.stderr)
    if issues:
        for issue in issues:
            print("PROBLEM: " + issue, file=sys.stderr)
        return 1
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(render(document))
    print("wrote %s: %d reflection entries, %d resources, %d dropped" % (
        output, len(document["reflection"]), len(document["resources"]), len(dropped)))
    return 0


def command_check(args):
    path = Path(args.file)
    text = path.read_text()
    document = json.loads(text)
    issues = problems(document, Classpath.from_file(args.classpath) if args.classpath else None)
    if entry_point_problem():
        issues.append(entry_point_problem())
    if render(document) != text:
        issues.append("file is not in canonical form; regenerate it with 'refresh' or reformat")
    for issue in issues:
        print("PROBLEM: " + issue, file=sys.stderr)
    if not issues:
        print("%s OK: %d reflection entries, %d resources" % (
            path, len(document.get("reflection", [])), len(document.get("resources", []))))
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
        # A condition that is present is kept, and one type under two conditions stays two entries.
        explicit = {"reflection": [{"type": "sample.Model", "fields": [{"name": "c"}],
                                    "condition": {"typeReached": "sample.Local"}}],
                    "resources": [{"glob": "sample/data.json",
                                   "condition": {"typeReached": "sample.Local"}}]}
        mixed, _ = curate([first, second, explicit], classpath)
        assert [(e["type"], e["condition"]["typeReached"]) for e in mixed["reflection"]
                if e["type"] == "sample.Model"] == [("sample.Model", SERVER_ENTRY_POINT),
                                                    ("sample.Model", "sample.Local")]
        assert [e["condition"]["typeReached"] for e in mixed["resources"]
                if e.get("glob") == "sample/data.json"] == [SERVER_ENTRY_POINT, "sample.Local"]
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
    print("native-metadata selftest OK")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    refresh = commands.add_parser("refresh", help="curate agent output into the bundled file")
    refresh.add_argument("trace", nargs="+",
                         help="agent output directories (or files) to merge")
    refresh.add_argument("--classpath", required=True,
                         help="Native classpath: bootstrap/target/native/classpath.args or a path list")
    refresh.add_argument("--output", default=DEFAULT_METADATA)
    refresh.set_defaults(handler=command_refresh)
    check = commands.add_parser("check", help="verify a committed metadata file")
    check.add_argument("file", nargs="?", default=DEFAULT_METADATA)
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
