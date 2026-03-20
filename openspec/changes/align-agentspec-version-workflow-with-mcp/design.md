## Context

`console-ui-next` is migrating AI registry modules toward a shared interaction model. MCP detail already exposes version switching, version history, and new-version creation as top-level header actions, while AgentSpec detail still concentrates version lifecycle management in a right-side timeline card. AgentSpec also differs in lifecycle semantics: version editing is draft-based through existing `createDraft`, `updateDraft`, `submit`, `publish`, `online`, and `offline` endpoints rather than through an explicit version-number form like MCP.

The change needs to make AgentSpec feel operationally consistent with MCP without introducing a backend contract change or breaking existing AgentSpec draft/review lifecycle rules.

## Goals / Non-Goals

**Goals:**
- Add MCP-style header-level version controls to AgentSpec detail.
- Provide a dedicated version history panel for AgentSpec detail.
- Provide an explicit AgentSpec new-version flow that starts from the currently viewed version while still using existing draft APIs.
- Keep the change contained to `console-ui-next` state, routing, UI components, copy, and tests.

**Non-Goals:**
- Changing AgentSpec backend APIs or lifecycle states.
- Redefining how AgentSpec version numbers are assigned during submit/publish.
- Reworking unrelated AgentSpec resource editing behavior.
- Making AgentSpec visually identical to MCP in areas unrelated to version management.

## Decisions

### 1. Add MCP-style header controls while reusing AgentSpec version data already returned by detail APIs

AgentSpec detail already receives `versions`, `version`, `editingVersion`, and related lifecycle metadata from `agentSpecApi.getDetail`. The page will promote this data into header-level actions: a version dropdown, a version-history button, and a new-version button.

Rationale:
- This delivers the requested MCP-style workflow without adding extra fetches or a new store slice.
- It keeps the version source of truth in the existing AgentSpec detail response.

Alternative considered:
- Add a separate AgentSpec version-list API call for the header. Rejected because the current detail payload already contains the required version summaries.

### 2. Use a dedicated history sheet for focused browsing, backed by the existing version timeline content

AgentSpec will add a dedicated history panel opened from the header, matching MCP's focused history access pattern. The implementation should reuse the existing `VersionTimeline` data model and action wiring where practical so action validity, ordering, and lifecycle buttons remain consistent.

Rationale:
- Reuse minimizes divergence between the inline lifecycle view and the new focused history entry point.
- The current timeline component already encodes valid version actions, so duplicating that logic would create avoidable drift.

Alternative considered:
- Replace the existing timeline card entirely. Rejected because the user request is parity for capabilities, not a mandatory removal of the current inline management surface.

### 3. Introduce `mode=version` for AgentSpec editor, but implement it as a draft-backed workflow

Unlike MCP, AgentSpec editing is draft-centric. The new-version action will navigate to `newAgentSpec` with a dedicated version mode and the currently viewed base version in query params. In version mode, the page will ensure a draft exists for the AgentSpec based on the selected version, then load the editable content through the existing draft/update flow.

Expected behavior:
- If no editing draft exists, version mode creates a draft based on the selected version before loading the editor.
- If an editing draft already exists, version mode reuses that draft instead of attempting to create a second one.
- Saving in version mode continues to use `updateDraft`; version numbering remains governed by existing submit/publish lifecycle rules.

Rationale:
- This preserves backend lifecycle constraints while still giving users the MCP-like explicit entry point for “new version”.
- Keeping the creation side effect in editor mode mirrors MCP's route-driven behavior more closely than mutating detail-page state first.

Alternative considered:
- Call `createDraft` directly in the detail page and navigate to `mode=edit`. Rejected because it hides version intent in routing and makes the detail page responsible for a side effect that belongs to the editor entry flow.

### 4. Keep version selection state local to AgentSpec detail instead of introducing a new deep-link contract

Version dropdown changes will reload the page data for the selected version and update the current view state, but this change will not require a new public URL format for AgentSpec detail.

Rationale:
- MCP parity for this request is interaction parity, not URL parity.
- Avoiding a new URL contract limits surface area and regression risk.

Alternative considered:
- Add a `version` query param to AgentSpec detail routes. Rejected for now because it is not required to deliver the requested workflow and would broaden routing changes.

## Risks / Trade-offs

- [Two version-management entry points could diverge] → Reuse shared version list ordering and action logic between the history sheet and existing timeline content.
- [Draft creation in version mode may conflict with an existing editing draft] → Detect `editingVersion` first and reuse the existing draft path instead of blindly creating a second draft.
- [Users may expect MCP-style explicit version-number editing] → Keep copy and button labels focused on “new version” while preserving existing AgentSpec submit-time version semantics.
- [State refreshes could cause visual flicker when switching versions] → Follow the existing store pattern of preserving current detail data while the selected version refreshes.

## Migration Plan

1. Update AgentSpec detail UI, editor routing, and local i18n strings in `console-ui-next`.
2. Add focused tests for version ordering, dropdown selection, and version-mode navigation/initialization.
3. Validate the flow against existing AgentSpec draft lifecycle endpoints.
4. Roll back by removing the new header actions and `mode=version` handling if regressions appear; no backend migration is required.

## Open Questions

- None identified for this frontend-only change; existing AgentSpec draft APIs are sufficient for the targeted parity.