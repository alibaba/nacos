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

describe('Config editor compact layout', () => {
  it('uses responsive horizontal form rows for primary fields', () => {
    expect(CONFIG_EDITOR_SOURCE).toContain("md:grid-cols-[4rem_minmax(0,1fr)]");
    expect(CONFIG_EDITOR_SOURCE).toContain('<Card className="gap-3 py-4">');
    expect(CONFIG_EDITOR_SOURCE).toContain('rows={2}');
  });

  it('keeps application and tags in collapsed advanced options', () => {
    expect(CONFIG_EDITOR_SOURCE).toContain(
      'const [advancedOpen, setAdvancedOpen] = useState(false);',
    );
    expect(CONFIG_EDITOR_SOURCE).toContain('<Collapsible open={advancedOpen}');
    expect(CONFIG_EDITOR_SOURCE).toContain("t('config.advancedOptions')");
    expect(CONFIG_EDITOR_SOURCE).toContain('space-y-4 px-4 pb-4 pt-3');
  });
});
