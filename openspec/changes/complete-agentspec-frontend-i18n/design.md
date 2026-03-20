## Context

`console-ui-next` already uses `react-i18next` and locale bundles in `src/locales/en-US.json` and `src/locales/zh-CN.json`, and most AgentSpec pages are wired to `t('agentSpec.*')`. The remaining gaps are concentrated in three areas:

- Missing locale keys for AgentSpec-specific lifecycle and status labels that are already referenced by the UI, such as list-card state badges and version-timeline status text.
- Inline fallback/default strings in components like `UploadAgentSpecDialog`, which hide incomplete locale coverage instead of making translation requirements explicit.
- Hardcoded English strings in shared AgentSpec editor components, including loading copy, read-only indicators, and accessibility labels for resizing or tree actions.

This change is frontend-only and should remain within AgentSpec pages, shared registry/editor components used by AgentSpec, locale JSON files, and tests that validate those workflows.

## Goals / Non-Goals

**Goals:**
- Ensure all AgentSpec user-facing text in list, detail, upload, and editor workflows resolves from locale resources.
- Add the missing locale keys required by AgentSpec cards, version timeline, upload dialog, file tree, and resource viewer states.
- Internationalize accessibility-facing strings that are exposed through `aria-label`, `title`, or editor loading placeholders in AgentSpec flows.
- Add regression coverage for the most error-prone locale-dependent AgentSpec states.

**Non-Goals:**
- Changing backend APIs, AgentSpec lifecycle rules, or route structure.
- Refactoring unrelated shared components that are not used by AgentSpec flows.
- Introducing runtime locale loading, new i18n libraries, or a new message architecture.
- Rewriting existing copy beyond what is required to complete missing localization coverage.

## Decisions

### 1. Complete locale coverage by making missing keys explicit in the existing locale bundles

The implementation should add the missing `agentSpec` keys and any small shared/common keys needed by AgentSpec-only components directly to both `en-US.json` and `zh-CN.json`, rather than relying on fallback strings embedded in component code.

Rationale:
- This keeps the existing `react-i18next` structure intact.
- Missing keys become visible in review and test diffs instead of being silently masked by inline defaults.

Alternative considered:
- Keep fallback strings in components for “safety.” Rejected because the current gaps are precisely caused by fallback copy obscuring incomplete locale resources.

### 2. Treat accessibility and structural UI strings as part of localization scope

AgentSpec internationalization should cover not only visible headings and buttons, but also `aria-label`, `title`, loading placeholders, read-only indicators, and editor/file-tree helper strings that appear in AgentSpec workflows.

Rationale:
- These strings are user-facing for assistive technology users and for mixed-language environments.
- Shared components like `FileTreePanel` and `ResourceViewer` currently expose hardcoded English even when page-level labels are localized.

Alternative considered:
- Limit the change to visible copy only. Rejected because it would leave incomplete language switching in core AgentSpec editing flows.

### 3. Keep translation ownership close to the existing AgentSpec namespace, with small shared keys only when reuse is obvious

AgentSpec workflow labels, lifecycle terms, and editor-flow copy should remain under `agentSpec.*`. Only generic strings already shared across the app, such as existing `common.*` actions, should stay in shared namespaces.

Rationale:
- This avoids scattering AgentSpec behavior across unrelated locale sections.
- It preserves the current component pattern where AgentSpec pages already consume `agentSpec.*` keys.

Alternative considered:
- Move editor/file-tree strings into a new generic registry namespace. Rejected because the immediate problem is missing AgentSpec localization, and introducing a new namespace would broaden scope without clear reuse need.

### 4. Add focused regression tests around required locale-backed states rather than snapshotting entire locale files

Tests should assert behavior where missing translations are most likely to regress: version timeline status rendering, AgentSpec card state badges, upload-dialog labels, and editor shared-component strings used by AgentSpec pages.

Rationale:
- This catches missing keys and accidental reintroduction of hardcoded strings in critical workflows.
- Focused tests are cheaper to maintain than broad locale snapshots.

Alternative considered:
- Add a broad test that snapshots all locale JSON content. Rejected because it is noisy and does not verify component usage.

## Risks / Trade-offs

- [Shared components may also be used outside AgentSpec] → Limit wording changes to neutral or AgentSpec-safe strings and verify current usage before editing shared copy keys.
- [New locale keys can drift between `en-US` and `zh-CN`] → Add keys to both bundles in one change set and include tests for the newly required AgentSpec states.
- [Some hardcoded strings may be easy to miss in nested editor components] → Audit AgentSpec pages together with `FileTreePanel`, `ResourceViewer`, `VersionTimeline`, `UploadAgentSpecDialog`, and `AgentSpecCard` instead of only page containers.
- [Replacing fallbacks may expose missing keys elsewhere during development] → Treat surfaced missing-key warnings as desired feedback and close them within the same change.

## Migration Plan

1. Add the missing AgentSpec locale keys in both locale bundles.
2. Replace fallback/default strings and hardcoded AgentSpec workflow copy in the identified pages and shared components.
3. Add or update focused frontend tests that cover the newly localized states.
4. Run the relevant `console-ui-next` AgentSpec test suite and fix regressions before applying the change.
5. Roll back by restoring the previous component strings and locale entries if needed; no backend or persisted-data migration is involved.

## Open Questions

- None identified. The current locale architecture is sufficient for this frontend-only completion work.