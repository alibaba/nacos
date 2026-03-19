import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Plus, Download, FileDown, Wrench } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import ToolList from './ToolList';
import ToolDetail from './ToolDetail';
import ToolEditorDialog from './ToolEditorDialog';
import ImportMcpToolsDialog from './ImportMcpToolsDialog';
import ImportOpenApiDialog from './ImportOpenApiDialog';
import type { McpTool, McpToolMeta, McpToolSpecification, McpSecurityScheme } from '@/types/mcp';

interface ToolManagerProps {
  toolSpec: McpToolSpecification;
  onChange: (toolSpec: McpToolSpecification) => void;
  /** Which import methods are available: 'openapi' (HTTP转换), 'mcp' (SSE/Streamable), 'none' (stdio) */
  importMode?: 'openapi' | 'mcp' | 'none';
}

export default function ToolManager({ toolSpec, onChange, importMode = 'none' }: ToolManagerProps) {
  const { t } = useTranslation();
  const tools = toolSpec.tools || [];
  const toolsMeta = toolSpec.toolsMeta || {};

  const [selectedTool, setSelectedTool] = useState<string | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingTool, setEditingTool] = useState<McpTool | null>(null);
  const [editingMeta, setEditingMeta] = useState<McpToolMeta | null>(null);
  const [mcpImportOpen, setMcpImportOpen] = useState(false);
  const [openApiImportOpen, setOpenApiImportOpen] = useState(false);

  const selectedToolObj = tools.find((t) => t.name === selectedTool) || null;
  const selectedToolMeta = selectedTool ? toolsMeta[selectedTool] : undefined;

  const updateSpec = useCallback(
    (
      newTools: McpTool[],
      newMeta: Record<string, McpToolMeta>,
      extraSchemes?: McpSecurityScheme[]
    ) => {
      const spec: McpToolSpecification = {
        ...toolSpec,
        tools: newTools,
        toolsMeta: newMeta,
      };
      if (extraSchemes && extraSchemes.length > 0) {
        const existing = toolSpec.securitySchemes || [];
        const existingIds = new Set(existing.map((s) => s.id));
        const merged = [...existing, ...extraSchemes.filter((s) => !existingIds.has(s.id))];
        spec.securitySchemes = merged;
      }
      onChange(spec);
    },
    [toolSpec, onChange]
  );

  // Create new tool
  const handleNewTool = () => {
    setEditingTool(null);
    setEditingMeta(null);
    setEditorOpen(true);
  };

  // Edit existing tool
  const handleEditTool = () => {
    if (!selectedToolObj) return;
    setEditingTool(selectedToolObj);
    setEditingMeta(selectedToolMeta || null);
    setEditorOpen(true);
  };

  // Delete tool
  const handleDeleteTool = () => {
    if (!selectedTool) return;
    const newTools = tools.filter((t) => t.name !== selectedTool);
    const newMeta = { ...toolsMeta };
    delete newMeta[selectedTool];
    setSelectedTool(null);
    updateSpec(newTools, newMeta);
  };

  // Save tool from editor
  const handleSaveTool = (tool: McpTool, meta: McpToolMeta) => {
    const isEdit = editingTool !== null;
    let newTools: McpTool[];
    if (isEdit) {
      newTools = tools.map((t) => (t.name === tool.name ? tool : t));
    } else {
      newTools = [...tools, tool];
    }
    const newMeta = { ...toolsMeta, [tool.name]: meta };
    updateSpec(newTools, newMeta);
    setSelectedTool(tool.name);
  };

  // Import from MCP instance
  const handleMcpImport = (importedTools: McpTool[], importedMeta: Record<string, McpToolMeta>) => {
    const existingNames = new Set(tools.map((t) => t.name));
    const newTools = [...tools];
    const newMeta = { ...toolsMeta };

    for (const tool of importedTools) {
      if (existingNames.has(tool.name)) {
        // Update existing
        const idx = newTools.findIndex((t) => t.name === tool.name);
        if (idx >= 0) newTools[idx] = tool;
      } else {
        newTools.push(tool);
      }
      newMeta[tool.name] = importedMeta[tool.name] || { enabled: true };
    }
    updateSpec(newTools, newMeta);
  };

  // Import from OpenAPI
  const handleOpenApiImport = (
    importedTools: McpTool[],
    importedMeta: Record<string, McpToolMeta>,
    securitySchemes: McpSecurityScheme[]
  ) => {
    const existingNames = new Set(tools.map((t) => t.name));
    const newTools = [...tools];
    const newMeta = { ...toolsMeta };

    for (const tool of importedTools) {
      if (existingNames.has(tool.name)) {
        const idx = newTools.findIndex((t) => t.name === tool.name);
        if (idx >= 0) newTools[idx] = tool;
      } else {
        newTools.push(tool);
      }
      newMeta[tool.name] = importedMeta[tool.name] || { enabled: true };
    }
    updateSpec(newTools, newMeta, securitySchemes);
  };

  return (
    <>
      <Card className="overflow-hidden py-0 gap-0">
        <div className="px-5 py-3.5 border-b bg-muted/30">
          <div className="flex items-center justify-between">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Wrench className="h-4 w-4 text-amber-500" />
              {t('mcp.toolManagement')}
              {tools.length > 0 && (
                <Badge variant="secondary" className="h-5 min-w-5 rounded-full text-[11px] font-semibold px-1.5 bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300">
                  {tools.length}
                </Badge>
              )}
            </h2>
            <div className="flex items-center gap-1.5">
              {/* Import dropdown - only show when imports are available */}
              {importMode !== 'none' && (
                importMode === 'openapi' ? (
                  <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setOpenApiImportOpen(true)}>
                    <FileDown className="h-3.5 w-3.5 mr-1.5" />
                    {t('mcp.importFromOpenApi')}
                  </Button>
                ) : (
                  <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setMcpImportOpen(true)}>
                    <Download className="h-3.5 w-3.5 mr-1.5" />
                    {t('mcp.importFromMcpInstance')}
                  </Button>
                )
              )}

              {/* New tool button */}
              <Button size="sm" className="h-7 text-xs" onClick={handleNewTool}>
                <Plus className="h-3.5 w-3.5 mr-1.5" />
                {t('mcp.newTool')}
              </Button>
            </div>
          </div>
        </div>

        <CardContent className="p-0">
          {tools.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
              <Wrench className="h-10 w-10 mb-3 opacity-30" />
              <p className="text-sm">{t('mcp.noToolsYet')}</p>
            </div>
          ) : (
            <div className="flex" style={{ height: '400px' }}>
              {/* Left panel - tool list */}
              <div className="w-60 border-r shrink-0 overflow-hidden bg-muted/20">
                <ToolList
                  tools={tools}
                  toolsMeta={toolsMeta}
                  selectedTool={selectedTool}
                  onSelect={setSelectedTool}
                />
              </div>

              {/* Right panel - tool detail */}
              <div className="flex-1 min-w-0 overflow-y-auto">
                {selectedToolObj ? (
                  <ToolDetail
                    tool={selectedToolObj}
                    meta={selectedToolMeta}
                    onEdit={handleEditTool}
                    onDelete={handleDeleteTool}
                  />
                ) : (
                  <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                    {t('mcp.noTools')}
                  </div>
                )}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Dialogs */}
      <ToolEditorDialog
        open={editorOpen}
        onOpenChange={setEditorOpen}
        tool={editingTool}
        meta={editingMeta}
        existingNames={tools.filter((t) => t.name !== editingTool?.name).map((t) => t.name)}
        onSave={handleSaveTool}
      />

      <ImportMcpToolsDialog
        open={mcpImportOpen}
        onOpenChange={setMcpImportOpen}
        onImport={handleMcpImport}
      />

      <ImportOpenApiDialog
        open={openApiImportOpen}
        onOpenChange={setOpenApiImportOpen}
        onImport={handleOpenApiImport}
      />
    </>
  );
}
