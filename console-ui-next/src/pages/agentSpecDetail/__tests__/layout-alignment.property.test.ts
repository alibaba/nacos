/**
 * Property-based tests for AgentSpec detail page layout alignment.
 *
 * These tests read the actual source code and verify CSS class patterns
 * to ensure layout consistency with the MCP detail page.
 */
import { describe, it, expect } from 'vitest';
import fc from 'fast-check';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../../agentSpecDetail/index.tsx'),
  'utf-8',
);

// ── Helpers ────────────────────────────────────────────────────────────────

/**
 * Extract all Card element blocks from the source.
 * Matches `<Card className="...">` and captures the className value.
 */
function extractCardClassNames(src: string): string[] {
  const re = /<Card\s+className="([^"]+)"/g;
  const results: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    results.push(m[1]);
  }
  return results;
}

/**
 * Extract all card header div blocks — the `<div className="...">` that
 * immediately follow a `<Card` opening and contain `border-b`.
 * We look for divs with className containing both `border-b` and `bg-muted`.
 */
function extractCardHeaderClassNames(src: string): string[] {
  const re = /<div\s+className="([^"]*border-b[^"]*)"/g;
  const results: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    results.push(m[1]);
  }
  return results;
}

/**
 * Extract all card header title elements — `<h2 className="...">` inside
 * card headers.
 */
function extractCardTitleClassNames(src: string): string[] {
  const re = /<h2\s+className="([^"]+)"/g;
  const results: string[] = [];
  let m: RegExpExecArray | null;
  while ((m = re.exec(src)) !== null) {
    results.push(m[1]);
  }
  return results;
}

// ── Pre-extracted data ─────────────────────────────────────────────────────

const cardClassNames = extractCardClassNames(SOURCE);
const cardHeaderClassNames = extractCardHeaderClassNames(SOURCE);
const cardTitleClassNames = extractCardTitleClassNames(SOURCE);

// ── Task 6.1: Property 1 — Card style consistency ─────────────────────────

/**
 * Property 1: 卡片样式一致性
 *
 * For all Card elements in the AgentSpec detail page, each Card must contain
 * `overflow-hidden`, `py-0`, and `gap-0` classes. Each card header must
 * contain `border-b` and `bg-muted/30`. Each card title must contain
 * `text-sm` and `font-semibold`.
 *
 * **Validates: Requirements 5.1, 5.2, 5.3**
 */
describe('Property 1: 卡片样式一致性 (Card style consistency)', () => {
  const REQUIRED_CARD_CLASSES = ['overflow-hidden', 'py-0', 'gap-0'];
  const REQUIRED_HEADER_CLASSES = ['border-b', 'bg-muted/30'];
  const REQUIRED_TITLE_CLASSES = ['text-sm', 'font-semibold'];

  it('should have at least one Card element in the source', () => {
    expect(cardClassNames.length).toBeGreaterThan(0);
  });

  it('property: every Card contains all required card classes', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...REQUIRED_CARD_CLASSES),
        (requiredClass) => {
          for (const cn of cardClassNames) {
            expect(cn).toContain(requiredClass);
          }
        },
      ),
      { numRuns: REQUIRED_CARD_CLASSES.length * 5 },
    );
  });

  it('property: every card header contains all required header classes', () => {
    // Filter to only headers inside the content grid (those with bg-muted/30)
    const contentHeaders = cardHeaderClassNames.filter((cn) =>
      cn.includes('bg-muted'),
    );
    expect(contentHeaders.length).toBeGreaterThan(0);

    fc.assert(
      fc.property(
        fc.constantFrom(...REQUIRED_HEADER_CLASSES),
        (requiredClass) => {
          for (const cn of contentHeaders) {
            expect(cn).toContain(requiredClass);
          }
        },
      ),
      { numRuns: REQUIRED_HEADER_CLASSES.length * 5 },
    );
  });

  it('property: every card title contains all required title classes', () => {
    expect(cardTitleClassNames.length).toBeGreaterThan(0);

    fc.assert(
      fc.property(
        fc.constantFrom(...REQUIRED_TITLE_CLASSES),
        (requiredClass) => {
          for (const cn of cardTitleClassNames) {
            expect(cn).toContain(requiredClass);
          }
        },
      ),
      { numRuns: REQUIRED_TITLE_CLASSES.length * 5 },
    );
  });

  it('property: random subsets of required card classes all appear in every Card', () => {
    fc.assert(
      fc.property(
        fc.subarray(REQUIRED_CARD_CLASSES, { minLength: 1 }),
        (subset) => {
          for (const cn of cardClassNames) {
            for (const cls of subset) {
              expect(cn).toContain(cls);
            }
          }
        },
      ),
      { numRuns: 50 },
    );
  });
});

// ── Task 6.2: Property 2 — Layout structure consistency ───────────────────

/**
 * Property 2: 布局结构一致性
 *
 * For any rendering state (loading or loaded), the content grid must use
 * a two-column desktop layout with a flexible primary pane and a fixed-width
 * 320px right rail, ensuring the IDE expands to consume all remaining space.
 *
 * **Validates: Requirements 2.1, 2.2, 2.3, 6.1**
 */
describe('Property 2: 布局结构一致性 (Layout structure consistency)', () => {
  const REQUIRED_GRID_CLASSES = ['lg:grid-cols-[minmax(0,1fr)_320px]'];
  const REQUIRED_RIGHT_COL_CLASSES = ['lg:w-[320px]'];

  // Split source into loading skeleton section and loaded section
  const skeletonSectionMatch = SOURCE.match(
    /\/\/\s*=+\s*Loading skeleton\s*=+[\s\S]*?return\s*\(\s*([\s\S]*?)\s*\);\s*\}/,
  );
  const skeletonSection = skeletonSectionMatch
    ? skeletonSectionMatch[1]
    : '';

  // Alternatively, find the content grid in the main return
  const contentGridSection = SOURCE.slice(
    SOURCE.indexOf('Content Grid'),
  );

  it('should find the loading skeleton section', () => {
    expect(skeletonSection.length).toBeGreaterThan(0);
  });

  it('loading skeleton uses the fixed-width right rail grid', () => {
    expect(skeletonSection).toContain('lg:grid-cols-[minmax(0,1fr)_320px]');
  });

  it('loaded state uses the fixed-width right rail grid', () => {
    expect(contentGridSection).toContain('lg:grid-cols-[minmax(0,1fr)_320px]');
  });

  it('loaded state keeps the right rail at 320px', () => {
    expect(contentGridSection).toContain('lg:w-[320px]');
  });

  it('property: both skeleton and loaded state contain all required grid classes', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...REQUIRED_GRID_CLASSES),
        (requiredClass) => {
          expect(skeletonSection).toContain(requiredClass);
          expect(contentGridSection).toContain(requiredClass);
        },
      ),
      { numRuns: REQUIRED_GRID_CLASSES.length * 5 },
    );
  });

  it('property: loaded state contains all required right rail classes', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...REQUIRED_RIGHT_COL_CLASSES),
        (requiredClass) => {
          expect(contentGridSection).toContain(requiredClass);
        },
      ),
      { numRuns: REQUIRED_RIGHT_COL_CLASSES.length * 5 },
    );
  });

  it('property: random subsets of desktop layout classes all appear where expected', () => {
    const ALL_LAYOUT_CLASSES = [
      ...REQUIRED_GRID_CLASSES,
      ...REQUIRED_RIGHT_COL_CLASSES,
    ];

    fc.assert(
      fc.property(
        fc.subarray(ALL_LAYOUT_CLASSES, { minLength: 1 }),
        (subset) => {
          for (const cls of subset) {
            if (REQUIRED_GRID_CLASSES.includes(cls)) {
              expect(skeletonSection).toContain(cls);
            }
            expect(contentGridSection).toContain(cls);
          }
        },
      ),
      { numRuns: 50 },
    );
  });
});
