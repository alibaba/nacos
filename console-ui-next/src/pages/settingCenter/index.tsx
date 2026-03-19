import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  Bot, Save, Info, Eye, EyeOff, Loader2,
} from 'lucide-react';

import client from '@/api/client';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

interface CopilotConfig {
  apiKey: string;
  model: string;
  studioUrl: string;
  studioProject: string;
}

const QWEN_MODELS = [
  { value: 'qwen-turbo', label: 'qwen-turbo', desc: 'Fast' },
  { value: 'qwen-plus', label: 'qwen-plus', desc: 'Enhanced' },
  { value: 'qwen-max', label: 'qwen-max', desc: 'Strongest' },
  { value: 'qwen-7b-chat', label: 'qwen-7b-chat', desc: '7B' },
  { value: 'qwen-14b-chat', label: 'qwen-14b-chat', desc: '14B' },
  { value: 'qwen-72b-chat', label: 'qwen-72b-chat', desc: '72B' },
  { value: 'qwen3-turbo', label: 'qwen3-turbo', desc: 'Qwen3 Fast' },
  { value: 'qwen3-plus', label: 'qwen3-plus', desc: 'Qwen3 Enhanced' },
  { value: 'qwen3-max', label: 'qwen3-max', desc: 'Qwen3 Strongest' },
  { value: 'qwen3-7b-instruct', label: 'qwen3-7b-instruct', desc: '7B' },
  { value: 'qwen3-14b-instruct', label: 'qwen3-14b-instruct', desc: '14B' },
  { value: 'qwen3-32b-instruct', label: 'qwen3-32b-instruct', desc: '32B' },
  { value: 'qwen3-72b-instruct', label: 'qwen3-72b-instruct', desc: '72B' },
];

export default function SettingCenterPage() {
  const { t } = useTranslation();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  // Form state
  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('qwen-turbo');
  const [studioUrl, setStudioUrl] = useState('');
  const [studioProject, setStudioProject] = useState('NacosCopilot');

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const response = await client.get('v3/console/copilot/config');
      const body = response as unknown as { data: CopilotConfig };
      const config = body.data || ({} as CopilotConfig);
      setApiKey(config.apiKey || '');
      setModel(config.model || 'qwen-turbo');
      // Remove trailing slash from studioUrl
      let url = config.studioUrl || '';
      if (url.endsWith('/')) url = url.slice(0, -1);
      setStudioUrl(url);
      setStudioProject(config.studioProject || 'NacosCopilot');
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadConfig();
  }, [loadConfig]);

  const handleSave = async () => {
    setSaving(true);
    try {
      let url = studioUrl.trim();
      if (url.endsWith('/')) url = url.slice(0, -1);

      const config: CopilotConfig = {
        apiKey: apiKey.trim(),
        model: model || 'qwen-turbo',
        studioUrl: url,
        studioProject: studioProject.trim() || 'NacosCopilot',
      };

      await client.post('v3/console/copilot/config', JSON.stringify(config), {
        headers: { 'Content-Type': 'application/json' },
      });
      toast.success(t('settings.saveSuccess'));
      loadConfig();
    } catch {
      // Error handled by interceptor
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-6 max-w-2xl">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-foreground">{t('settings.title')}</h1>
      </div>

      {/* Copilot Config Card */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center gap-2 mb-6">
            <Bot className="h-5 w-5 text-primary" />
            <h2 className="text-base font-semibold">{t('settings.copilotConfig')}</h2>
          </div>

          {loading ? (
            <div className="space-y-6">
              {Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex flex-col gap-2">
                  <Skeleton className="h-4 w-24" />
                  <Skeleton className="h-10 w-full" />
                </div>
              ))}
            </div>
          ) : (
            <div className="flex flex-col gap-5">
              {/* API Key */}
              <div className="flex flex-col gap-2">
                <Label className="flex items-center gap-1.5">
                  {t('settings.apiKey')}
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <Info className="h-3.5 w-3.5 text-muted-foreground cursor-help" />
                    </TooltipTrigger>
                    <TooltipContent className="max-w-[260px]">
                      {t('settings.apiKeyHint')}
                    </TooltipContent>
                  </Tooltip>
                </Label>
                <div className="relative">
                  <Input
                    type={showApiKey ? 'text' : 'password'}
                    placeholder={t('settings.apiKeyPlaceholder')}
                    value={apiKey}
                    onChange={(e) => setApiKey(e.target.value)}
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(!showApiKey)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  >
                    {showApiKey ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {/* Model */}
              <div className="flex flex-col gap-2">
                <Label>{t('settings.model')}</Label>
                <Select value={model} onValueChange={setModel}>
                  <SelectTrigger>
                    <SelectValue placeholder={t('settings.modelPlaceholder')} />
                  </SelectTrigger>
                  <SelectContent>
                    {QWEN_MODELS.map((m) => (
                      <SelectItem key={m.value} value={m.value}>
                        <span>{m.label}</span>
                        <span className="ml-2 text-muted-foreground text-xs">({m.desc})</span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Studio URL */}
              <div className="flex flex-col gap-2">
                <Label>{t('settings.studioUrl')}</Label>
                <Input
                  placeholder={t('settings.studioUrlPlaceholder')}
                  value={studioUrl}
                  onChange={(e) => setStudioUrl(e.target.value)}
                />
              </div>

              {/* Studio Project */}
              <div className="flex flex-col gap-2">
                <Label>{t('settings.studioProject')}</Label>
                <Input
                  placeholder={t('settings.studioProjectPlaceholder')}
                  value={studioProject}
                  onChange={(e) => setStudioProject(e.target.value)}
                />
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Save Button */}
      <div className="flex justify-end">
        <Button onClick={handleSave} disabled={saving || loading} className="gap-2 min-w-[100px]">
          {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          {saving ? t('common.loading') : t('common.save')}
        </Button>
      </div>
    </div>
  );
}
