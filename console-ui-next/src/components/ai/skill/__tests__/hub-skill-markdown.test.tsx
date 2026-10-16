import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { HubSkillMarkdown } from '../HubSkillMarkdown';

describe('shared Skill read-only preview', () => {
  it('does not execute raw scripts, event handlers or javascript links', () => {
    const html = renderToStaticMarkup(<HubSkillMarkdown source={'<script>alert(1)</script>\n\n<img src=x onerror="alert(1)">\n\n[unsafe](javascript:alert(1))'} />);
    expect(html).not.toContain('<script');
    expect(html).not.toContain('onerror');
    expect(html).not.toContain('javascript:');
  });
  it('renders the pinned markdown body while keeping YAML metadata out of the preview', () => {
    const html = renderToStaticMarkup(<HubSkillMarkdown source={'---\nname: internal-name\n---\n# Version 1.0.0\n\nPinned instructions.'} />);
    expect(html).toContain('Version 1.0.0');
    expect(html).toContain('Pinned instructions.');
    expect(html).not.toContain('internal-name');
  });
  it('retains code and table content without edit controls', () => {
    const html = renderToStaticMarkup(<HubSkillMarkdown source={'| A | B |\n| - | - |\n| 1 | 2 |\n\n```sh\necho skill\n```'} />);
    expect(html).toContain('<table>');
    expect(html).toContain('echo skill');
    expect(html).not.toContain('<textarea');
  });
});
