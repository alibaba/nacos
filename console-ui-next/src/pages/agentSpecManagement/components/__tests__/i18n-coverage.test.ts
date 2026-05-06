import fs from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

function readJson(relativePath: string) {
  return JSON.parse(
    fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8'),
  ) as Record<string, unknown>;
}

function readSource(relativePath: string) {
  return fs.readFileSync(path.resolve(__dirname, relativePath), 'utf8');
}

function getPathValue(record: Record<string, unknown>, dottedPath: string) {
  return dottedPath.split('.').reduce<unknown>((current, segment) => {
    if (!current || typeof current !== 'object') {
      return undefined;
    }
    return (current as Record<string, unknown>)[segment];
  }, record);
}

describe('AgentSpec i18n coverage', () => {
  const en = readJson('../../../../locales/en-US.json');
  const zh = readJson('../../../../locales/zh-CN.json');

  const requiredKeys = [
    'agentSpec.newFile',
    'agentSpec.newFolder',
    'agentSpec.fileTreeTitle',
    'agentSpec.fileTree',
    'agentSpec.editorLoading',
    'agentSpec.modified',
    'agentSpec.readOnly',
    'agentSpec.resizeFileTreePanel',
    'agentSpec.renameNode',
    'agentSpec.deleteNode',
    'agentSpec.createFileIn',
    'agentSpec.createFolderIn',
    'agentSpec.descriptionPlaceholder',
    'agentSpec.editing',
    'agentSpec.reviewing',
    'agentSpec.submit',
    'agentSpec.publish',
    'agentSpec.online',
    'agentSpec.offline',
    'agentSpec.createTime',
    'agentSpec.versionStatus.draft',
    'agentSpec.versionStatus.reviewing',
    'agentSpec.versionStatus.online',
    'agentSpec.versionStatus.offline',
  ];

  it('defines the required AgentSpec locale keys in both bundles', () => {
    for (const key of requiredKeys) {
      expect(getPathValue(en, key), `missing en key: ${key}`).toBeTruthy();
      expect(getPathValue(zh, key), `missing zh key: ${key}`).toBeTruthy();
    }
  });

  it('removes inline fallback strings from the upload dialog', () => {
    const source = readSource('../UploadAgentSpecDialog.tsx');

    expect(source).not.toContain("t('agentSpec.invalidZipFile',");
    expect(source).not.toContain("t('agentSpec.uploadSuccess',");
    expect(source).not.toContain("t('agentSpec.uploadFailed',");
    expect(source).not.toContain("t('agentSpec.uploadZip',");
    expect(source).not.toContain("t('agentSpec.uploadZipDesc',");
    expect(source).not.toContain("t('agentSpec.dragOrClick',");
    expect(source).not.toContain("t('agentSpec.upload',");
  });

  it('uses dedicated version status keys for timeline badges', () => {
    const source = readSource('../VersionTimeline.tsx');

    expect(source).toContain('agentSpec.versionStatus.${v.status}');
    expect(source).not.toContain('agentSpec.status.${v.status}');
  });

  it('does not leave shared AgentSpec editor strings hardcoded in source files', () => {
    const fileTreeSource = readSource('../FileTreePanel.tsx');
    const resourceViewerSource = readSource('../ResourceViewer.tsx');
    const editorSource = readSource('../../../newAgentSpec/index.tsx');

    for (const source of [fileTreeSource, resourceViewerSource, editorSource]) {
      expect(source).not.toContain('"Loading editor..."');
      expect(source).not.toContain('"Read Only"');
      expect(source).not.toContain('"Resize file tree panel"');
    }

    expect(fileTreeSource).not.toContain('"New File"');
    expect(fileTreeSource).not.toContain('"New Folder"');
    expect(fileTreeSource).not.toContain('"File tree"');
    expect(editorSource).not.toContain('>Modified<');
    expect(editorSource).not.toContain('"Modified"');
  });
});