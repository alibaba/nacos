<!--
  Copyright 1999-2026 Alibaba Group Holding Ltd.
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->

# ARD conformance probes

This wrapper runs the original upstream runner pinned to
`f073b1ea9b01a5f068d0b0986426f2ea49fa30b2`. It requires schema validation instead
of accepting the upstream runner's missing-dependency or missing-schema warnings
as a pass. Schema references resolve offline from hash-verified fixtures; probes
still access the target service. No schema is downloaded from upstream `main`.

Use Python 3.9 or later in a virtual environment:

```sh
python3 -m venv /tmp/ard-probe-venv
/tmp/ard-probe-venv/bin/python -m pip install -r test/scripts/requirements-ard-conformance.txt
/tmp/ard-probe-venv/bin/python test/scripts/test_ard_conformance.py
/tmp/ard-probe-venv/bin/python test/scripts/ard-conformance.py manifest http://localhost:9080/.well-known/ard.json
/tmp/ard-probe-venv/bin/python test/scripts/ard-conformance.py registry http://localhost:9080/v3/ai/ard
/tmp/ard-probe-venv/bin/python test/scripts/ard-conformance.py publisher https://registry.example.com
```

`manifest` also accepts a local JSON file. `publisher` requires an HTTPS origin
and resolves `/.well-known/ard.json`; the upstream legacy fallback is retained
with a warning. A controlled HTTPS fixture is publisher transport evidence only,
not proof of a public deployment. `--ca-file` can trust a test deployment's CA;
TLS verification is never disabled.

For authenticated endpoints, put a dedicated token in an environment variable
and pass its name with `--token-env ARD_TEST_TOKEN`. Do not put the token in the
command arguments. Requests and redirects stay on the original scheme, host and
port; cross-origin redirects are rejected. Only probe trusted test targets.

Exit codes are `0` for successful probes, `1` for invalid content or failed
probes, and `2` for missing dependencies or unusable pinned fixtures. CLI usage
errors also return `2`. Unknown references, validator exceptions and missing URI
or date-time validators cannot silently pass. Manifest/publisher entries use the
full-entry schema; Search/List use the partial-entry schemas. Lowercase
`trustManifest` is explicitly checked with the pinned TrustManifest definition
without rewriting upstream files.

The upstream Registry probe uses one fixed query and may return an empty result.
Its optional List/Explore operations accept 404/501. A pass does not prove resource
completeness, private authorization, Artifact correctness, identity ownership,
Web Ingestion, federation or overall ARD Registry conformance. Warnings remain
warnings, including legacy publisher fallback and missing query examples.

Standalone `ArdAdaptorOpenApiITCase` complements the probes with nonempty public
Agent discovery, exact Version/digest artifacts, contexts, pagination, facets and
legacy catalogs. Authentication tests exercise real identities separately. Private
MCP recall and canonical-name/compatible-ID Artifact mapping remain unresolved;
the disabled cross-type scenario is not counted as passed. See the
[scenario matrix](../openapi-test/AI_REGISTRY_ADAPTOR_API_TEST_SCENARIOS.md) and
[adaptation spec](../../specs/en/ai/ard-v091-adaptation-spec.md) for the acceptance
boundary. These probes do not promote the overall compatibility baseline.
