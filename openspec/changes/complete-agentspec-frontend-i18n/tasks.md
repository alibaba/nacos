## 1. Locale Coverage Audit And Key Completion

- [x] 1.1 Audit AgentSpec pages and shared AgentSpec components for missing locale-backed strings, including version timeline, upload dialog, card badges, file tree, and resource viewer copy.
- [x] 1.2 Add the missing AgentSpec lifecycle, status, editor, and accessibility keys to both `console-ui-next/src/locales/en-US.json` and `console-ui-next/src/locales/zh-CN.json`.

## 2. Replace Hardcoded And Fallback Copy

- [x] 2.1 Update AgentSpec components that currently use inline fallback/default strings so required text resolves only through locale keys.
- [x] 2.2 Replace hardcoded strings in AgentSpec editor/file-tree/resource-viewer flows, including loading text, read-only indicators, resize labels, and tree action labels, with translated copy.
- [x] 2.3 Normalize AgentSpec version and lifecycle labels across list, detail, and timeline views so all rendered states use the completed locale keys.

## 3. Regression Coverage And Validation

- [x] 3.1 Add or update focused tests covering newly localized AgentSpec states, especially timeline statuses, upload-dialog copy, and shared editor-component text used by AgentSpec flows.
- [x] 3.2 Run the relevant `console-ui-next` AgentSpec frontend tests and fix any regressions introduced by the localization completion work.