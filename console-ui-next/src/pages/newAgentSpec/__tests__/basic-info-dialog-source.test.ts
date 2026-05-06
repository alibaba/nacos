import { describe, expect, it } from 'vitest';
import fs from 'fs';
import path from 'path';

const SOURCE = fs.readFileSync(
  path.resolve(__dirname, '../index.tsx'),
  'utf-8',
);

describe('AgentSpec editor basic info dialog source', () => {
  it('includes label editing in the basic info dialog and persists labels on save', () => {
    expect(SOURCE).toContain("const [labels, setLabels] = useState<Record<string, string>>({});");
    expect(SOURCE).toContain("const [description, setDescription] = useState('');");
    expect(SOURCE).toContain('syncManifestDescription');
    expect(SOURCE).toContain('<Textarea');
    expect(SOURCE).toContain("placeholder={t('agentSpec.descriptionPlaceholder')}");
    expect(SOURCE).toContain('setDescription(trimmedDescription);');
    expect(SOURCE).toContain('content: syncManifestDescription(manifestFile.content, trimmedDescription),');
    expect(SOURCE).toContain('<LabelEditor');
    expect(SOURCE).toContain("showSaveButton={false}");
    expect(SOURCE).toContain('await agentSpecApi.updateLabels({');
  });

  it('supports save-and-publish by saving the draft and then submitting it for review', () => {
    expect(SOURCE).toContain("const [draftVersion, setDraftVersion] = useState('');");
    expect(SOURCE).toContain('const persistDraft = useCallback(async (showSaveToast = true) => {');
    expect(SOURCE).toContain('description,');
    expect(SOURCE).toContain('content: syncManifestDescription(manifestFile.content, description),');
    expect(SOURCE).toContain('await agentSpecApi.submit({');
    expect(SOURCE).toContain("t('agentSpec.saveAndPublish')");
  });

  it('keeps description state in sync with manifest.json edits', () => {
    expect(SOURCE).toContain('const nextDescription = getAgentSpecDescription(newValue);');
    expect(SOURCE).toContain('setDescription(nextDescription);');
    expect(SOURCE).toContain('setDraftDescription(nextDescription);');
  });
});