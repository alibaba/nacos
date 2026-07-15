import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SIDEBAR_SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../sidebar.tsx'),
  'utf-8',
);

describe('Sidebar platform layout', () => {
  it('keeps setting center under platform management with the existing copilot gate', () => {
    const settingsBlock = SIDEBAR_SOURCE.slice(
      SIDEBAR_SOURCE.indexOf('if (copilotEnabled)'),
      SIDEBAR_SOURCE.indexOf('const navTo = useCallback'),
    );

    expect(settingsBlock).toContain('platformItems.push');
    expect(settingsBlock).toContain("key: 'settings'");
    expect(settingsBlock).toContain("label: t('menu.settingCenter')");
    expect(settingsBlock).toContain("path: '/settingCenter'");
  });

  it('auto-expands platform management when setting center is active', () => {
    const platformPathsBlock = SIDEBAR_SOURCE.slice(
      SIDEBAR_SOURCE.indexOf('const platformPaths = ['),
      SIDEBAR_SOURCE.indexOf('if (platformPaths.some'),
    );

    expect(platformPathsBlock).toContain("'/settingCenter'");
  });

  it('leaves only version information in the sidebar bottom section', () => {
    const bottomBlock = SIDEBAR_SOURCE.slice(
      SIDEBAR_SOURCE.indexOf('{/* Bottom Section */}'),
      SIDEBAR_SOURCE.indexOf('</aside>'),
    );

    expect(bottomBlock).toContain('{version && `v${version}`}');
    expect(bottomBlock).toContain('{startupMode && ` · ${startupMode}`}');
    expect(bottomBlock).not.toContain('settingCenter');
    expect(bottomBlock).not.toContain('toggleSidebar');
    expect(bottomBlock).not.toContain('PanelLeft');
  });
});
