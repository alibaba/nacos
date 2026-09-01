import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';

import {
  VersionLifecycleActionBar,
  VersionLifecycleActionDivider,
} from '../VersionLifecycleActionBar';

describe('VersionLifecycleActionBar', () => {
  it('shares the lifecycle layout and renders an optional warning', () => {
    const html = renderToStaticMarkup(createElement(
      VersionLifecycleActionBar,
      {
        className: 'custom-actions',
        warning: createElement('span', null, 'warning'),
        children: createElement('button', null, 'submit'),
      },
    ));

    expect(html).toContain('border-t');
    expect(html).toContain('flex-wrap');
    expect(html).toContain('custom-actions');
    expect(html).toContain('warning');
    expect(html).toContain('submit');
  });

  it('renders the shared vertical action-group divider', () => {
    const html = renderToStaticMarkup(createElement(VersionLifecycleActionDivider));

    expect(html).toContain('aria-hidden="true"');
    expect(html).toContain('w-px');
    expect(html).toContain('bg-border');
  });
});
