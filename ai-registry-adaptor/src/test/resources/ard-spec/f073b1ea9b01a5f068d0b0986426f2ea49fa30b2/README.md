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

# ARD v0.91 test fixtures

Source: [ards-project/ard-spec](https://github.com/ards-project/ard-spec/tree/f073b1ea9b01a5f068d0b0986426f2ea49fa30b2).
Pin: `f073b1ea9b01a5f068d0b0986426f2ea49fa30b2`; Apache-2.0 (original LICENSE included).
All files below retain upstream bytes. OpenAPI info.version remains 0.5.0;
it does not identify the protocol revision.

| Source path | SHA-256 |
| --- | --- |
| spec/schemas/ard-entry.schema.json | 3032dad432607ddae0ec088db5759fa456f36308e0e9f340165aaed504d535f4 |
| spec/schemas/ard.openapi.yaml | 13767927d9985fb4503fffd228c371ac08297b2ac80a7e6c8a1125cd3cf1f025 |
| spec/schemas/ard.context.jsonld | bd80a73de23c4279409a429f43edcf944e07c09511678511bc9467924067c093 |
| conformance/bin/conformance-test | 428b6cc17e8bb76c796c79b30953a02d70e92a6a9da8d0ef43027f7e968c1c7c |
| LICENSE | dfe0e2a538e0e9004d43d1f57598177793109f5662706ccd1b1cb93c7fa34ce5 |

ArdContractSchemas verifies hashes, enables URI format assertions and resolves
references only from pinned local documents; missing/modified files and unknown
references fail rather than downloading main. YAML is parsed into the equivalent
JSON tree in memory; source files are unchanged. Legacy fixtures stay independent.

The upstream EntryFields property is spelled `TrustManifest`, while the protocol
uses `trustManifest`. Tests explicitly apply the original TrustManifest definition
to the lowercase value as a supplemental semantic check. They do not rewrite the
fixture or claim identity/signature verification. Full entries and partial
Search/List results use their distinct definitions.
