## ADDED Requirements

### Requirement: AgentSpec workflows SHALL be fully locale-driven
All AgentSpec frontend workflows in `console-ui-next` SHALL resolve user-facing copy from locale resources instead of embedding hardcoded English or Chinese text in component code.

#### Scenario: List, detail, upload, and editor flows render localized copy
- **WHEN** an operator opens AgentSpec management, AgentSpec detail, the upload dialog, or the AgentSpec editor in a supported locale
- **THEN** all visible workflow labels, buttons, headings, helper text, and status text SHALL render from locale keys defined for that locale

### Requirement: AgentSpec lifecycle and version states SHALL have explicit locale keys
The frontend SHALL define explicit locale entries for AgentSpec lifecycle and version-related labels used by list cards, detail pages, and the version timeline, including state badges and action labels.

#### Scenario: Version timeline renders translated status labels
- **WHEN** the AgentSpec version timeline renders versions in states such as draft, reviewing, online, or offline
- **THEN** the state badge for each version SHALL be resolved through locale keys dedicated to those states rather than by raw state codes or missing-key output

#### Scenario: AgentSpec cards render translated lifecycle summaries
- **WHEN** an AgentSpec list card shows editing, reviewing, or online summary badges
- **THEN** those summary labels SHALL use locale-backed text in both supported locale bundles

### Requirement: AgentSpec accessibility-facing strings SHALL be localized
Accessibility-facing strings used in AgentSpec workflows, including `aria-label`, `title`, editor loading placeholders, and read-only indicators, SHALL be localizable through the same locale system.

#### Scenario: Shared editor components expose localized accessibility text
- **WHEN** an operator uses the AgentSpec file tree or resource viewer with assistive technologies or hover titles
- **THEN** resize handles, tree action buttons, loading states, and read-only indicators SHALL use translated strings from locale resources

### Requirement: AgentSpec locale coverage SHALL not depend on inline fallback copy
Components used by AgentSpec workflows SHALL NOT rely on embedded fallback/default strings as the primary source for required locale-backed UI text.

#### Scenario: Upload dialog has explicit locale coverage
- **WHEN** the AgentSpec upload dialog is rendered or reports a validation/upload error
- **THEN** its title, description, empty prompt, validation message, success message, failure message, and submit action SHALL be backed by locale keys defined in both supported bundles