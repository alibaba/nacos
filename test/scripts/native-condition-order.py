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

"""Check the condition types of the bundled Native metadata against a run of the Server.

An entry is available at run time once the type of its condition starts initializing. So the
condition type of an owner is early enough if it is initialized before the first class of the
owner's entries is loaded or initialized in a run. This script compares exactly that, in the
class logs of one or more runs::

    python3 test/scripts/native-trace-server.sh classlog <log> <nacos-home>
    python3 test/scripts/native-condition-order.py /path/to/start.log /path/to/restart.log

Exercise the Server as the Native CI job does (smoke test, restart check) before taking the
logs. Loads during class verification do not count as lookups, and resources are not compared:
a class log does not show when a resource is read (the ``exact`` CI variant reports those).
An owner in REASONED_CONDITIONS of native-metadata.py is listed with its reason. The exit code
is 1 if a condition type is initialized too late, never initialized although its entries were
used, or missing for an owner.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import re
import sys

LOAD = re.compile(r"^\[\s*[\d.]+s\] (\S+) source: ")
INIT = re.compile(r"^\[\s*[\d.]+s\] \d+ Initializing '([^']+)'")
VERIFY_START = re.compile(r"^\[\s*[\d.]+s\] Start class verification for: ")
VERIFY_END = re.compile(r"^\[\s*[\d.]+s\] End class verification for: ")
JDK = ("java.", "javax.", "jdk.", "sun.", "com.sun.")


def load_tool():
    spec = importlib.util.spec_from_file_location(
        "native_metadata", Path(__file__).resolve().parent / "native-metadata.py")
    tool = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(tool)
    return tool


def parse(log):
    """Line numbers of the first use of each class and of its first initialization.

    A class is used when it is loaded, unless the JVM verifies another class at that moment, or
    when it is initialized, whichever comes first.
    """
    use, init, verifying = {}, {}, 0
    with open(log, errors="replace") as handle:
        for number, line in enumerate(handle):
            if VERIFY_START.match(line):
                verifying += 1
            elif VERIFY_END.match(line):
                verifying = max(0, verifying - 1)
            else:
                found = LOAD.match(line)
                if found:
                    if not verifying:
                        use.setdefault(found.group(1), number)
                    continue
                found = INIT.match(line)
                if found:
                    name = found.group(1).replace("/", ".")
                    init.setdefault(name, number)
                    use.setdefault(name, number)
    return use, init


def entry_classes(document):
    """The classes that the reflection entries of a document name, arrays by their element type."""
    classes = set()
    for entry in document.get("reflection", []):
        kind = entry["type"]
        for name in [kind] if isinstance(kind, str) else kind.get("proxy", []):
            name = re.sub(r"(\[\])+$", "", name)
            if not name.startswith(JDK):
                classes.add(name)
    return classes


def main():
    tool = load_tool()
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("log", nargs="+", help="class logs of runs of the Server")
    parser.add_argument("--bundle", default=tool.DEFAULT_BUNDLE, help="the bundled metadata")
    args = parser.parse_args()
    logs = {Path(log).name: parse(log) for log in args.log}
    root = Path(tool.__file__).resolve().parents[2] / args.bundle
    problems, confirmed, reasoned, resources_only, unreached = [], [], [], [], []
    for owner, file in tool.bundle_files(root).items():
        if owner in (tool.NACOS_OWNER, tool.JDK_OWNER):
            continue
        condition = tool.owner_condition(owner)
        if condition is None:
            problems.append("%s has no condition type in CONDITION_GROUPS" % owner)
            continue
        classes = entry_classes(json.loads(file.read_text()))
        if not classes:
            resources_only.append((owner, condition))
            continue
        reached = False
        for name, (use, init) in logs.items():
            first = min(((use[c], c) for c in classes if c in use), default=None)
            if first is None:
                continue
            reached = True
            at = init.get(condition)
            if owner in tool.REASONED_CONDITIONS:
                continue
            if at is None:
                problems.append("%s: %s was never initialized, but %s was used (%s)" % (
                    owner, condition, first[1], name))
            elif at >= first[0]:
                problems.append("%s: %s was initialized after %s was used (%s)" % (
                    owner, condition, first[1], name))
        if not reached:
            unreached.append((owner, condition))
        elif owner in tool.REASONED_CONDITIONS:
            reasoned.append((owner, condition, tool.REASONED_CONDITIONS[owner]))
        else:
            confirmed.append(owner)
    print("%d owners: the condition type is initialized before their first entry class is used"
          % len(confirmed))
    for owner, condition, reason in reasoned:
        print("not comparable, accepted for a reason, %s (%s): %s" % (owner, condition, reason))
    for owner, condition in resources_only:
        print("only resource entries, which a class log does not show: %s (%s)" % (
            owner, condition))
    for owner, condition in unreached:
        print("no entry class was used, nothing compared: %s (%s)" % (owner, condition))
    for problem in problems:
        print("PROBLEM: " + problem, file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
