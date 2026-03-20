## Why

AgentSpec detail currently exposes version lifecycle actions through a right-side timeline, but it does not provide the MCP-aligned primary workflow of header-level version switching, a dedicated version history entry point, and explicit creation of a new version from the current version. This makes two adjacent AI registry modules behave differently for the same operator task and increases learning cost during the ongoing console-ui-next migration.

## What Changes

- Add an MCP-aligned version selector to the AgentSpec detail header so operators can switch the viewed version from the primary action area.
- Add a dedicated version history entry point for AgentSpec detail that presents historical versions in a focused panel instead of relying only on the inline timeline card.
- Add an explicit “new version” action for AgentSpec that starts version creation from the currently viewed version, matching MCP navigation and wording.
- Update AgentSpec page state, routing, and supporting copy so version selection, history viewing, and version creation remain consistent with existing AgentSpec lifecycle rules.
- Add or update focused tests covering version ordering, version selection behavior, and new-version navigation for AgentSpec.

## Capabilities

### New Capabilities
- `agentspec-version-workflow`: Define the required AgentSpec detail and editor behaviors for version switching, version history access, and creating a new version in parity with MCP.

### Modified Capabilities
- None.

## Impact

- Affected UI: `console-ui-next/src/pages/agentSpecDetail`, `console-ui-next/src/pages/newAgentSpec`, and shared AgentSpec version-management components.
- Affected client state/API surface: AgentSpec store, AgentSpec API typings, and route/query-param handling in `console-ui-next`.
- Affected UX assets: AgentSpec i18n copy and version-management tests in `console-ui-next`.
- No backend API contract change is expected; the change should reuse existing AgentSpec version endpoints where possible.