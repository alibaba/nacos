import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { authApi } from '@/api';
import { useServerStore } from '@/stores/server-store';

export default function RegisterPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { authAdminRequest, stateLoaded, fetchState } = useServerStore();

  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const fetched = useRef(false);

  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchState();
  }, [fetchState]);

  // Redirect if not in admin request mode
  useEffect(() => {
    if (!stateLoaded) return;
    if (authAdminRequest === false) {
      navigate('/login', { replace: true });
    }
  }, [stateLoaded, authAdminRequest, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    try {
      await authApi.admin({ username: 'nacos', password });
      toast.success(t('common.success'));
      navigate('/login', { replace: true });
    } catch {
      toast.error(t('common.failed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-blue-50 via-sky-100 to-blue-100" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(255,255,255,0.6),transparent_60%)]" />

      <Card className="relative z-10 w-full max-w-md mx-4 border-0 bg-white shadow-2xl shadow-blue-200/40">
        <CardContent className="p-8">
          <div className="flex items-center gap-3 mb-6">
            <img src={`${import.meta.env.BASE_URL}img/nacos-logo-dark.svg`} alt="Nacos" className="h-8" />
          </div>
          <h2 className="text-xl font-bold text-gray-800 mb-1">{t('login.initPassword')}</h2>

          <p className="text-sm text-gray-600 mb-2">{t('login.internalSysTip3')}</p>
          <p className="text-xs text-gray-500 mb-6">{t('login.internalSysTip4')}</p>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label className="text-gray-700">Username</Label>
              <Input value="nacos" disabled className="h-10 bg-gray-50 border-gray-200" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password" className="text-gray-700">{t('login.pleaseInputPasswordTips')}</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t('login.pleaseInputPassword')}
                className="h-10 border-gray-200 focus:border-blue-400 focus:ring-blue-400"
              />
            </div>
            <Button
              type="submit"
              disabled={loading}
              className={cn(
                'w-full h-10 bg-gradient-to-r from-blue-500 to-blue-600 text-white font-medium shadow-md shadow-blue-500/25',
                'hover:from-blue-600 hover:to-blue-700 hover:shadow-lg hover:shadow-blue-500/30 transition-all duration-200'
              )}
            >
              {loading && <Loader2 size={16} className="animate-spin mr-2" />}
              {t('login.submit')}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
