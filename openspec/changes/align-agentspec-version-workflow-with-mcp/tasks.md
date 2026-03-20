## 1. AgentSpec Detail Version Controls

- [x] 1.1 Add header-level version selector and version history trigger to the AgentSpec detail page using existing AgentSpec detail version data.
- [x] 1.2 Reuse or adapt the existing AgentSpec version timeline rendering so the dedicated history panel shows versions in descending update-time order with the correct lifecycle actions.
- [x] 1.3 Add a header-level new-version action on AgentSpec detail that uses the currently viewed version as the source version.

## 2. AgentSpec Version-Mode Editor Flow

- [x] 2.1 Extend `newAgentSpec` route handling with a dedicated version mode and source-version query-param support.
- [x] 2.2 Implement draft initialization for version mode so the editor creates a draft from the selected source version only when no editing draft already exists.
- [x] 2.3 Keep save behavior in version mode on the existing AgentSpec draft update API and update page copy/badges so users can distinguish new, edit, and version modes.

## 3. Validation And Coverage

- [x] 3.1 Update AgentSpec i18n strings for header version controls, version history, and version-mode editor states.
- [x] 3.2 Add or update focused tests for AgentSpec version ordering, header version selection, history-panel selection, and new-version navigation/initialization behavior.
- [x] 3.3 Run the relevant `console-ui-next` test suite covering AgentSpec version-management behavior and fix regressions introduced by the change.