## Why

AgentSpec frontend pages in `console-ui-next` have partial internationalization coverage, but several user-facing strings still bypass locale resources through missing translation keys, inline fallback copy, and hardcoded labels in shared editor/file-tree components. As AgentSpec management becomes a primary AI registry workflow, these gaps make language switching incomplete and create inconsistent UX between Chinese and English environments.

## What Changes

- Audit AgentSpec-related frontend pages and shared components in `console-ui-next` to identify untranslated user-facing copy, including visible labels, action text, empty states, loading states, and accessibility labels.
- Add missing `agentSpec` and shared locale keys required by AgentSpec list, detail, upload, editor, file tree, resource viewer, and version timeline flows.
- Replace inline fallback/default strings and hardcoded English copy in AgentSpec UI components with locale-driven translations.
- Normalize AgentSpec version and lifecycle wording so list/detail/timeline surfaces use translated status and action labels consistently across both locale bundles.
- Add or update focused tests to prevent regression for AgentSpec locale keys that are required for rendered UI states and workflow-specific status labels.

## Capabilities

### New Capabilities
- `agentspec-frontend-localization`: Define required localization coverage for AgentSpec frontend pages and shared registry components, including lifecycle statuses, upload/editor flows, and accessibility-facing strings.

### Modified Capabilities
- None.

## Impact

- Affected UI: `console-ui-next/src/pages/agentSpecManagement`, `console-ui-next/src/pages/agentSpecDetail`, `console-ui-next/src/pages/newAgentSpec`, and shared AgentSpec components such as `VersionTimeline`, `UploadAgentSpecDialog`, `FileTreePanel`, `ResourceViewer`, and `AgentSpecCard`.
- Affected assets: `console-ui-next/src/locales/en-US.json` and `console-ui-next/src/locales/zh-CN.json`.
- Affected validation: AgentSpec frontend tests covering version workflow, file tree/editor behavior, and locale key usage.
- No backend API contract change is expected; this is a frontend-only localization completion change.