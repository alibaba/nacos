## ADDED Requirements

### Requirement: AgentSpec detail SHALL provide MCP-style header version switching
The AgentSpec detail page SHALL expose a version selector in the header action area whenever version summaries are available for the current AgentSpec. The selector SHALL show the currently viewed version and switching it SHALL reload the detail content for the selected version.

#### Scenario: Current version is shown in the header selector
- **WHEN** a user opens an AgentSpec detail page with one or more available versions
- **THEN** the header SHALL display a version selector whose active value matches the version currently shown in the detail content

#### Scenario: Selecting a different version reloads detail content
- **WHEN** a user selects another version from the AgentSpec header version selector
- **THEN** the page SHALL load and display the selected version's detail data without navigating away from the AgentSpec detail page

### Requirement: AgentSpec detail SHALL provide a dedicated version history panel
The AgentSpec detail page SHALL expose a version history action in the header that opens a focused panel containing the AgentSpec version history in reverse chronological order. The history panel SHALL allow the user to select a version and invoke the same lifecycle actions that are valid for that version.

#### Scenario: Header history action opens focused version browsing
- **WHEN** a user activates the AgentSpec version history action from the detail header
- **THEN** the system SHALL open a dedicated history panel listing the AgentSpec versions from newest update time to oldest

#### Scenario: Selecting a version from history updates the viewed version
- **WHEN** a user selects a version from the AgentSpec history panel
- **THEN** the system SHALL load that version's detail data and keep the selected version reflected in the detail view

### Requirement: AgentSpec SHALL offer explicit new-version creation from the current version
The AgentSpec detail page SHALL expose a new-version action in the header. Activating this action SHALL start an editor flow based on the version currently being viewed, while continuing to honor AgentSpec's existing single-draft lifecycle.

#### Scenario: New version starts from the currently viewed version
- **WHEN** a user activates the AgentSpec new-version action while viewing a specific version
- **THEN** the system SHALL open the AgentSpec editor in version mode using that viewed version as the source for the editable draft content

#### Scenario: Existing editing draft is reused
- **WHEN** a user activates the AgentSpec new-version action and the AgentSpec already has an active editing draft
- **THEN** the system SHALL reuse the existing editable draft instead of attempting to create a second draft for the same AgentSpec

### Requirement: AgentSpec editor SHALL support version-mode entry
The AgentSpec editor SHALL recognize a dedicated version-mode entry path and initialize the editing session for version creation without changing the existing save, submit, publish, online, or offline backend contracts.

#### Scenario: Version mode initializes an editable draft session
- **WHEN** the AgentSpec editor is opened in version mode for an AgentSpec without an existing editing draft
- **THEN** the system SHALL create a draft based on the requested source version before loading the editor content

#### Scenario: Version mode preserves existing draft save behavior
- **WHEN** a user saves an AgentSpec opened through version mode
- **THEN** the system SHALL persist the changes through the existing AgentSpec draft update workflow rather than introducing a new version-specific save API