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

"""Run pinned upstream ARD probes with mandatory, offline schema validation.

These probes do not certify resource completeness, authorization or federation.
See ard-conformance.md for the complementary standalone HTTP tests and limits.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import ssl
import sys
from types import ModuleType, SimpleNamespace
import urllib.error
import urllib.parse
import urllib.request

PIN = "f073b1ea9b01a5f068d0b0986426f2ea49fa30b2"
FIXTURES = (Path(__file__).resolve().parents[2] / "ai-registry-adaptor/src/test/resources"
            / "ard-spec" / PIN)
SOURCE = "https://raw.githubusercontent.com/ards-project/ard-spec/" + PIN + "/spec/schemas/"
HASHES = {
    "conformance-test": "428b6cc17e8bb76c796c79b30953a02d70e92a6a9da8d0ef43027f7e968c1c7c",
    "ard-entry.schema.json": "3032dad432607ddae0ec088db5759fa456f36308e0e9f340165aaed504d535f4",
    "ard.openapi.yaml": "13767927d9985fb4503fffd228c371ac08297b2ac80a7e6c8a1125cd3cf1f025",
}


class SetupError(Exception):
    """The validator cannot run reliably; never report a skipped check as a pass."""


class Schemas:
    def __init__(self, directory):
        try:
            import jsonschema
            import yaml
            from referencing import Registry, Resource
            from referencing.jsonschema import DRAFT202012
        except ImportError as error:
            raise SetupError("Install test/scripts/requirements-ard-conformance.txt") from error
        documents = {}
        for name, expected in HASHES.items():
            path = directory / name
            if not path.is_file():
                raise SetupError("Missing pinned fixture: " + name)
            raw = path.read_bytes()
            if hashlib.sha256(raw).hexdigest() != expected:
                raise SetupError("Modified pinned fixture: " + name)
            if name != "conformance-test":
                documents[name] = yaml.safe_load(raw) if name.endswith("yaml") else json.loads(raw)
        self.entry = documents["ard-entry.schema.json"]
        # Registry has no network retriever: every reference must resolve to pinned bytes.
        registry = Registry()
        for name, document in documents.items():
            resource = Resource.from_contents(document, default_specification=DRAFT202012)
            registry = registry.with_resource(SOURCE + name, resource)
            if "$id" in document:
                registry = registry.with_resource(document["$id"], resource)
        self.validator = jsonschema.Draft202012Validator
        self.registry = registry
        self.formats = jsonschema.FormatChecker()
        if not {"uri", "date-time"}.issubset(self.formats.checkers):
            raise SetupError("Install the required URI and date-time format validators")
        for schema in self.entry["$defs"].values():
            self.validator.check_schema(schema)
        for schema in documents["ard.openapi.yaml"]["components"]["schemas"].values():
            self.validator.check_schema(schema)

    def validate(self, filename, pointer, value):
        validator = self.validator({"$ref": SOURCE + filename + "#" + pointer},
                                   registry=self.registry, format_checker=self.formats)
        errors = list(validator.iter_errors(value))
        if errors:
            error = errors[0]
            path = "/".join(map(str, error.absolute_path)) or "<root>"
            raise ValueError(f"{pointer} fails {error.validator} at {path}")

    def trust(self, entries):
        # Upstream EntryFields spells this TrustManifest; the protocol uses lowercase.
        for entry in entries:
            if isinstance(entry, dict) and "trustManifest" in entry:
                self.validate("ard-entry.schema.json", "/$defs/TrustManifest",
                              entry["trustManifest"])

    def manifest(self, value):
        self.validate("ard-entry.schema.json", "/$defs/ArdManifest", value)
        self.trust(value["entries"])

    def response(self, definition, value):
        self.validate("ard.openapi.yaml", "/components/schemas/" + definition, value)
        self.trust(value.get("results", value.get("items", [])))


class SameOriginRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, newurl):
        if origin(request.full_url) != origin(newurl):
            raise urllib.error.HTTPError(request.full_url, code,
                                         "Cross-origin redirect refused", headers, response)
        return super().redirect_request(request, response, code, message, headers, newurl)


def origin(url):
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username is not None:
        raise ValueError("Expected an HTTP(S) URL without user information")
    return parsed.scheme, parsed.hostname.lower(), parsed.port or (443 if parsed.scheme == "https" else 80)


class ProbeResponse(io.BytesIO):
    def __init__(self, body, status):
        super().__init__(body)
        self.status = status


def load_runner(directory, schemas, opener, mode, target, headers):
    path = directory / "conformance-test"
    module = ModuleType("pinned_ard_runner")
    module.__file__ = str(path)
    # Execute verified source without generating bytecode beside vendored fixtures.
    exec(compile(path.read_bytes(), str(path), "exec"), module.__dict__)

    class StrictTester(module.ConformanceTester):
        def run_json_schema_validation(self, value):
            try:
                schemas.manifest(value)
                return True
            except Exception as error:
                self.add_error("Mandatory schema validation failed: " + str(error))
                return False

    tester = StrictTester()
    expected_origin = origin(target) if mode != "manifest" or target.startswith(("http://", "https://")) else None

    def checked_open(request, timeout=10):
        request = urllib.request.Request(request) if isinstance(request, str) else request
        if origin(request.full_url) != expected_origin:
            raise ValueError("Probe attempted a different origin")
        for name, value in headers.items():
            request.add_header(name, value)
        with opener.open(request, timeout=timeout) as response:
            body = response.read()
            if mode == "registry":
                definitions = {"/agents": "ListResponse", "/search": "SearchResponse",
                               "/explore": "ExploreResponse"}
                path = urllib.parse.urlsplit(request.full_url).path
                suffix = path[len(urllib.parse.urlsplit(target).path.rstrip("/")):]
                schemas.response(definitions[suffix], json.loads(body))
            return ProbeResponse(body, response.status)

    # Adapt only this loaded runner's transport; do not change global urllib behavior.
    module.urllib = SimpleNamespace(request=SimpleNamespace(Request=urllib.request.Request,
                                                          urlopen=checked_open),
                                   error=urllib.error)
    return tester, checked_open


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("manifest", "publisher", "registry"))
    parser.add_argument("target")
    parser.add_argument("--fixtures", type=Path, default=FIXTURES)
    parser.add_argument("--token-env", help="Environment variable containing the bearer token")
    parser.add_argument("--ca-file", help="Additional trusted CA for a controlled HTTPS deployment")
    args = parser.parse_args(argv)
    try:
        schemas = Schemas(args.fixtures)
        headers = {}
        if args.token_env:
            token = os.environ.get(args.token_env, "")
            if not token or "\r" in token or "\n" in token:
                raise SetupError("Token environment variable is missing, empty or invalid")
            headers["Authorization"] = "Bearer " + token
        target = args.target
        if args.mode == "publisher":
            target = target if "://" in target else "https://" + target
            parsed = urllib.parse.urlsplit(target)
            if (origin(target)[0] != "https" or parsed.path not in ("", "/")
                    or parsed.query or parsed.fragment):
                raise SetupError("Publisher mode requires an HTTPS origin")
        context = ssl.create_default_context()
        if args.ca_file:
            context.load_verify_locations(cafile=args.ca_file)
        opener = urllib.request.build_opener(SameOriginRedirect(), urllib.request.HTTPSHandler(context=context))
        tester, fetch = load_runner(args.fixtures, schemas, opener, args.mode, target, headers)
        if args.mode == "registry":
            success = tester.validate_registry(target)
        elif args.mode == "publisher":
            content, source = tester.resolve_publisher(target)
            success = content is not None and tester.validate_manifest(content, source)
        else:
            if target.startswith(("http://", "https://")):
                with fetch(target) as response:
                    content = response.read().decode("utf-8")
            else:
                content = Path(target).read_text(encoding="utf-8")
            success = tester.validate_manifest(content, target)
        success = success and not tester.errors
        print(f"ARD probe {'PASS' if success else 'FAIL'}: mode={args.mode}, pin={PIN}, "
              f"errors={len(tester.errors)}, warnings={len(tester.warnings)}. "
              "This is not a complete Registry conformance certification.")
        return 0 if success else 1
    except SetupError as error:
        print("ARD probe SETUP ERROR: " + str(error), file=sys.stderr)
        return 2
    except Exception as error:
        print("ARD probe FAIL: " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
