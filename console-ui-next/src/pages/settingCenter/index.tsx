import { useEffect, useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import {
  Bot, Save, Eye, EyeOff, Loader2,
} from 'lucide-react';

import client from '@/api/client';
import { useServerStore } from '@/stores/server-store';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent } from '@/components/ui/card';
import { Label } from '@/components/ui/label';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

interface CopilotConfig {
  apiKey: string;
  model: string;
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
  const navigate = useNavigate();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);
  const stateLoaded = useServerStore((s) => s.stateLoaded);

  useEffect(() => {
    if (stateLoaded && !copilotEnabled) {
      navigate('/', { replace: true });
    }
  }, [stateLoaded, copilotEnabled, navigate]);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  const [apiKey, setApiKey] = useState('');
  const [model, setModel] = useState('qwen-turbo');

  const loadConfig = useCallback(async () => {
    setLoading(true);
    try {
      const response = await client.get('v3/console/copilot/config');
      const body = response as unknown as { data: CopilotConfig };
      const config = body.data || ({} as CopilotConfig);
      setApiKey(config.apiKey || '');
      setModel(config.model || 'qwen-turbo');
    } catch {
      // Error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (copilotEnabled) {
      loadConfig();
    }
  }, [copilotEnabled, loadConfig]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const config: CopilotConfig = {
        apiKey: apiKey.trim(),
        model: model || 'qwen-turbo',
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
    <div className="flex flex-col gap-6 max-w-3xl">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-semibold text-foreground">{t('settings.title')}</h1>
        <p className="text-sm text-muted-foreground mt-1">{t('settings.description')}</p>
      </div>

      {/* Copilot Config Card */}
      {copilotEnabled && (
        <Card className="py-0">
          <CardContent className="py-6">
            {/* Section Header */}
            <div className="flex items-start gap-3 mb-6">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                <Bot className="h-[18px] w-[18px] text-primary" />
              </div>
              <div>
                <h2 className="text-base font-semibold leading-none mt-0.5">{t('settings.copilotConfig')}</h2>
                <p className="text-sm text-muted-foreground mt-1.5">{t('settings.copilotConfigDesc')}</p>
              </div>
            </div>

            {loading ? (
              <div className="space-y-6">
                {Array.from({ length: 2 }).map((_, i) => (
                  <div key={i} className="flex flex-col gap-2.5">
                    <Skeleton className="h-4 w-24" />
                    <Skeleton className="h-10 w-full" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="flex flex-col gap-5">
                {/* API Key */}
                <div className="space-y-2.5">
                  <Label>{t('settings.apiKey')}</Label>
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
                  <p className="text-xs text-muted-foreground">{t('settings.apiKeyHint')}</p>
                </div>

                {/* Model */}
                <div className="space-y-2.5">
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
              </div>
            )}

            {/* Save Action */}
            <div className="flex justify-end mt-6 pt-5 border-t">
              <Button onClick={handleSave} disabled={saving || loading} className="gap-2 min-w-[120px]">
                {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                {saving ? t('common.loading') : t('common.save')}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
