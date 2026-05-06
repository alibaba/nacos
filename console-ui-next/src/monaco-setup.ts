import * as monaco from 'monaco-editor';
import { loader, type Monaco } from '@monaco-editor/react';

import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker';
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker';

// Use locally bundled Monaco instead of CDN, so the editor works behind
// any contextPath / air-gapped deployment without network dependency.
self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'json') return new jsonWorker();
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker();
    return new editorWorker();
  },
};

loader.config({ monaco: monaco as unknown as Monaco });
