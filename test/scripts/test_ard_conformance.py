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

"""CLI failure propagation and pinned protocol fixtures; no Nacos process required."""
import contextlib
import copy
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest import mock

SCRIPT = Path(__file__).with_name("ard-conformance.py")
SPEC = importlib.util.spec_from_file_location("ard_probe", SCRIPT)
PROBE = importlib.util.module_from_spec(SPEC)
sys.dont_write_bytecode = True
SPEC.loader.exec_module(PROBE)
ENTRY = {"identifier": "urn:air:example.test:tools:weather", "displayName": "Weather",
         "type": "application/mcp-server-card+json", "data": {},
         "representativeQueries": ["weather today", "weather tomorrow"]}
MANIFEST = {"entries": [ENTRY]}
PARTIAL = {"identifier": ENTRY["identifier"], "score": 95, "source": "https://registry.example.test"}


class Service:
    def __init__(self, routes):
        self.routes = routes
        self.requests = []

    def __enter__(self):
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                owner.requests.append((self.path, dict(self.headers)))
                status, body = owner.routes.get(self.path, (404, {}))
                self.send_response(status)
                if status == 302:
                    self.send_header("Location", body)
                    body = {}
                self.end_headers()
                self.wfile.write(body if isinstance(body, bytes) else json.dumps(body).encode())

            do_POST = do_GET

            def log_message(self, *args):
                pass

        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.url = "http://127.0.0.1:" + str(self.server.server_port)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        return self

    def __exit__(self, *args):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()


def registry_routes():
    return {"/ard/agents": (200, {"items": [{"identifier": ENTRY["identifier"]}]}),
            "/ard/search": (200, {"results": [copy.deepcopy(PARTIAL)]}),
            "/ard/explore": (200, {"resultType": "facets", "facets": {}})}


class ProbeTest(unittest.TestCase):
    def run_cli(self, *args, env=None, no_site=False):
        result = subprocess.run([sys.executable] + (["-S"] if no_site else [])
                                + [str(SCRIPT), *map(str, args)],
                                capture_output=True, text=True, env=env, timeout=30)
        return result.returncode, result.stdout + result.stderr

    def manifest(self, value):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(value))
            return self.run_cli("manifest", path)

    def test_full_manifest_and_open_extensions(self):
        value = copy.deepcopy(MANIFEST)
        value["transportExtension"] = True
        value["entries"][0]["trustManifest"] = {"identity": "did:example:123", "custom": True}
        self.assertEqual(0, self.manifest(value)[0])

    def test_invalid_full_entries_are_failures(self):
        for update in ({"url": "https://example.test/card"}, {"data": None},
                       {"identifier": "not-a-urn"}, {"representativeQueries": [1]},
                       {"updatedAt": "not a timestamp"},
                       {"trustManifest": {}}, {"trustManifest": {"identity": 1}},
                       {"trustManifest": {"identity": "did:example:123", "attestations": [{}]}}):
            with self.subTest(update=update):
                value = copy.deepcopy(MANIFEST)
                value["entries"][0].update(update)
                self.assertEqual(1, self.manifest(value)[0])

    def test_uri_format_is_asserted(self):
        value = copy.deepcopy(MANIFEST)
        del value["entries"][0]["data"]
        value["entries"][0]["url"] = "not an absolute URI"
        self.assertEqual(1, self.manifest(value)[0])

    def test_partial_entry_is_not_a_full_manifest_entry(self):
        self.assertEqual(1, self.manifest({"entries": [PARTIAL]})[0])

    def test_missing_dependencies_fail_before_network(self):
        code, output = self.run_cli("registry", "http://127.0.0.1:1", no_site=True)
        self.assertEqual(2, code, output)
        self.assertIn("SETUP ERROR", output)

    def test_missing_or_modified_pinned_files_fail_before_network(self):
        for name in PROBE.HASHES:
            for modification in ("missing", "modified"):
                with self.subTest(name=name, modification=modification), tempfile.TemporaryDirectory() as directory:
                    fixtures = Path(directory)
                    for original in PROBE.HASHES:
                        shutil.copy(PROBE.FIXTURES / original, fixtures / original)
                    if modification == "missing":
                        (fixtures / name).unlink()
                    else:
                        with (fixtures / name).open("ab") as stream:
                            stream.write(b" ")
                    code, output = self.run_cli("registry", "http://127.0.0.1:1", "--fixtures", fixtures)
                    self.assertEqual(2, code, output)

    def test_validator_exception_cannot_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text(json.dumps(MANIFEST))
            with mock.patch.object(PROBE.Schemas, "manifest", side_effect=RuntimeError("validator unavailable")), \
                    contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(1, PROBE.main(["manifest", str(path)]))

    def test_missing_format_validators_cannot_pass(self):
        with mock.patch("jsonschema.FormatChecker") as checker:
            checker.return_value.checkers = {}
            with self.assertRaises(PROBE.SetupError):
                PROBE.Schemas(PROBE.FIXTURES)

    def test_malformed_manifest_json_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "manifest.json"
            path.write_text("{broken")
            self.assertEqual(1, self.run_cli("manifest", path)[0])

    def test_unknown_reference_has_no_network_retriever(self):
        schemas = PROBE.Schemas(PROBE.FIXTURES)
        with mock.patch("urllib.request.urlopen", side_effect=AssertionError("network access")) as network:
            with self.assertRaises(Exception):
                list(schemas.validator({"$ref": "https://unavailable.example/schema.json"},
                                       registry=schemas.registry).iter_errors({}))
            network.assert_not_called()

    def test_registry_accepts_partial_entries(self):
        with Service(registry_routes()) as service:
            code, output = self.run_cli("registry", service.url + "/ard")
        self.assertEqual(0, code, output)
        self.assertIn("not a complete Registry conformance certification", output)

    def test_registry_response_types_and_constraints(self):
        invalid = [("agents", {"items": "not a list"}),
                   ("agents", {"items": [{}]}),
                   ("search", {"results": [dict(PARTIAL, score=True)]}),
                   ("search", {"results": [dict(PARTIAL, source="relative")] }),
                   ("search", {"results": [], "unknown": True}),
                   ("search", {"results": [dict(PARTIAL, trustManifest={})]}),
                   ("explore", {"resultType": "facets", "facets": {"type": {"buckets": "wrong"}}})]
        with Service(registry_routes()) as service:
            for endpoint, body in invalid:
                with self.subTest(endpoint=endpoint, body=body):
                    service.routes = registry_routes()
                    service.routes["/ard/" + endpoint] = (200, body)
                    code, output = self.run_cli("registry", service.url + "/ard")
                    self.assertEqual(1, code, output)

    def test_optional_operations_allow_404_and_501(self):
        routes = registry_routes()
        routes.update({"/ard/agents": (404, {}), "/ard/explore": (501, {})})
        with Service(routes) as service:
            self.assertEqual(0, self.run_cli("registry", service.url + "/ard")[0])

    def test_mandatory_search_http_errors_and_malformed_json_fail(self):
        with Service(registry_routes()) as service:
            for response in ((401, {}), (500, {}), (404, {}), (200, b"not json")):
                with self.subTest(response=response):
                    service.routes["/ard/search"] = response
                    self.assertEqual(1, self.run_cli("registry", service.url + "/ard")[0])

    def test_authenticated_http_manifest_and_same_origin_redirect(self):
        import os
        routes = {"/manifest": (302, "/actual"), "/actual": (200, MANIFEST)}
        with Service(routes) as service:
            env = dict(os.environ, ARD_PROBE_TEST_TOKEN="probe-secret")
            code, output = self.run_cli("manifest", service.url + "/manifest", "--token-env",
                                        "ARD_PROBE_TEST_TOKEN", env=env)
        self.assertEqual(0, code, output)
        self.assertEqual(2, len(service.requests))
        self.assertTrue(all(headers.get("Authorization") == "Bearer probe-secret"
                            for _, headers in service.requests))
        self.assertNotIn("probe-secret", output)

    def test_cross_origin_redirect_does_not_forward_credentials(self):
        import os
        with Service({"/manifest": (200, MANIFEST)}) as foreign:
            with Service({"/manifest": (302, foreign.url + "/manifest")}) as service:
                code, output = self.run_cli("manifest", service.url + "/manifest", "--token-env",
                                            "ARD_PROBE_TEST_TOKEN", env=dict(os.environ, ARD_PROBE_TEST_TOKEN="secret"))
        self.assertEqual(1, code, output)
        self.assertEqual([], foreign.requests)

    def test_empty_token_is_a_setup_error(self):
        import os
        env = dict(os.environ)
        env.pop("ARD_PROBE_TEST_MISSING", None)
        code, output = self.run_cli("registry", "http://127.0.0.1:1", "--token-env",
                                    "ARD_PROBE_TEST_MISSING", env=env)
        self.assertEqual(2, code, output)

    def test_publisher_requires_https_origin(self):
        for target in ("http://publisher.example", "https://publisher.example/path",
                       "https://publisher.example?query=1", "https://publisher.example#fragment"):
            self.assertEqual(2, self.run_cli("publisher", target)[0])

    def test_user_information_in_target_url_is_rejected(self):
        for target in ("http://user:password@127.0.0.1:1", "http://:password@127.0.0.1:1"):
            self.assertEqual(1, self.run_cli("registry", target)[0])

    def test_publisher_current_and_legacy_resolution(self):
        import urllib.error
        for legacy in (False, True):
            requested = []

            def open_request(request, timeout):
                requested.append(request.full_url)
                if legacy and request.full_url.endswith("/ard.json"):
                    raise urllib.error.HTTPError(request.full_url, 404, "not found", {}, None)
                return PROBE.ProbeResponse(json.dumps(MANIFEST).encode(), 200)

            with mock.patch.object(PROBE.urllib.request, "build_opener") as builder, \
                    contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                builder.return_value.open.side_effect = open_request
                self.assertEqual(0, PROBE.main(["publisher", "publisher.example"]))
            self.assertEqual("https://publisher.example/.well-known/ard.json", requested[0])
            self.assertEqual(2 if legacy else 1, len(requested))


if __name__ == "__main__":
    unittest.main()
