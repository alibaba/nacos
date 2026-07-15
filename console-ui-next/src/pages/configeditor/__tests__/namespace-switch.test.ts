import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const CONFIG_EDITOR_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../index.tsx'),
  'utf-8',
);

describe('Config editor namespace switching', () => {
  it('registers a namespace switch guard when the loaded config has unpublished edits', () => {
    expect(CONFIG_EDITOR_SOURCE).toContain('setNamespaceChangeGuard(() => {');
    expect(CONFIG_EDITOR_SOURCE).toContain('if (!hasUnsavedChanges)');
    expect(CONFIG_EDITOR_SOURCE).toContain(
      "return window.confirm(t('config.unsavedNamespaceSwitchConfirm'));",
    );
  });

  it('tracks a loaded snapshot so namespace switching only prompts after edits', () => {
    expect(CONFIG_EDITOR_SOURCE).toContain('const [loadedSnapshot, setLoadedSnapshot]');
    expect(CONFIG_EDITOR_SOURCE).toContain('isSameConfigEditorSnapshot(currentSnapshot, loadedSnapshot)');
    expect(CONFIG_EDITOR_SOURCE).toContain('setLoadedSnapshot(nextSnapshot);');
  });

  it('clears stale editor state and returns to the selected namespace config list when config is missing', () => {
    expect(CONFIG_EDITOR_SOURCE).toContain('const redirectToConfigList');
    expect(CONFIG_EDITOR_SOURCE).toContain('clearEditorState();');
    expect(CONFIG_EDITOR_SOURCE).toContain('navigate(getConfigurationManagementPath(urlNamespace), { replace: true });');
    expect(CONFIG_EDITOR_SOURCE).toContain('redirectToConfigList(true);');
  });
});
