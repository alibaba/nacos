/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import fs from 'fs';
import path from 'path';
import { describe, expect, it } from 'vitest';

const EDITOR_SOURCE = fs.readFileSync(path.resolve(__dirname, '../SchemaEditor.tsx'), 'utf-8');
const DIALOG_SOURCE = fs.readFileSync(path.resolve(__dirname, '../ToolEditorDialog.tsx'), 'utf-8');

describe('MCP tool output schema nullable editor', () => {
  it('renders a nullable checkbox only when nullable editing is enabled', () => {
    expect(EDITOR_SOURCE).toContain('{allowNullable && (');
    expect(EDITOR_SOURCE).toContain('setSchemaNullable(schema.type, !!v)');
  });

  it('enables nullable editing for output schema but not input schema', () => {
    const inputEditor = '<SchemaEditor value={inputSchema} onChange={setInputSchema} />';
    const outputEditor =
      '<SchemaEditor value={outputSchema} onChange={setOutputSchema} allowNullable />';

    expect(DIALOG_SOURCE).toContain(inputEditor);
    expect(DIALOG_SOURCE).toContain(outputEditor);
  });
});
