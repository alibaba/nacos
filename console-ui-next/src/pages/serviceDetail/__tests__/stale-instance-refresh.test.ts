/**
 * Regression coverage for #15296: the instance list endpoint serves a cached
 * view that lags an accepted instance write, so refetching immediately after
 * updateInstance renders stale state (the toggle appeared to need two clicks).
 * The page must patch local state with the confirmed values instead of
 * refetching.
 */
import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(path.resolve(__dirname, '../index.tsx'), 'utf-8');

const sliceBetween = (start: string, end: string) => {
  const from = SOURCE.indexOf(start);
  const to = SOURCE.indexOf(end);
  expect(from).toBeGreaterThan(-1);
  expect(to).toBeGreaterThan(from);
  return SOURCE.slice(from, to);
};

describe('Service detail instance update refresh (#15296)', () => {
  it('imports the pure patch helper', () => {
    expect(SOURCE).toContain("from './instance-state'");
  });

  it('toggleInstance patches local state and does not refetch the cluster list', () => {
    const body = sliceBetween('const toggleInstance', 'const handleDeleteInstance');
    expect(body).toContain('patchInstance(');
    expect(body).toContain('nextEnabled');
    expect(body).not.toContain('refreshClusterInstances');
    expect(body).not.toContain('refreshDetail');
  });

  it('handleEditInstanceSubmit patches local state and does not refetch service detail', () => {
    const body = sliceBetween('const handleEditInstanceSubmit', 'const toggleInstance');
    expect(body).toContain('patchInstance(');
    expect(body).not.toContain('refreshDetail');
    expect(body).not.toContain('refreshClusterInstances');
  });

  it('handleDeleteInstance removes the row locally and does not refetch service detail', () => {
    const body = sliceBetween('const handleDeleteInstance', '// ===== Cluster pagination');
    expect(body).toContain('removeInstance(');
    expect(body).not.toContain('refreshDetail');
    expect(body).not.toContain('refreshClusterInstances');
    // The previous-page navigation is the one path that still has to fetch
    // (the page is not held locally); the local removal then scrubs the
    // deleted row if the fetched page is still served from the stale cache.
    expect(body).toContain('await fetchClusterInstances(');
  });
});
