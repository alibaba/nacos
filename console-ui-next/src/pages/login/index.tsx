import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Eye, EyeOff, Loader2, KeyRound } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { getContextPath } from '@/lib/sse-utils';

export default function LoginPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { login, loading: authLoading } = useAuthStore();
  const { fetchState, fetchGuide, stateLoaded, loginPageEnabled, authAdminRequest, authSystemType, consoleUiEnable, guideMsg } = useServerStore();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const fetched = useRef(false);

  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchState();
    fetchGuide();
  }, [fetchState, fetchGuide]);

  // Show OIDC error from sessionStorage
  useEffect(() => {
    const oidcError = sessionStorage.getItem('oidcError');
    if (oidcError) {
      toast.error(`${t('login.oidcAuthFailed')}: ${oidcError}`);
      sessionStorage.removeItem('oidcError');
    }
  }, [t]);

  const handleOidcLogin = () => {
    try {
      const loginUrl = `${getContextPath()}v1/auth/oidc/login`;
      console.log('[OIDC] Redirecting to:', loginUrl);
      window.location.href = loginUrl;
    } catch (error) {
      console.error('[OIDC] Failed to redirect to OIDC login:', error);
      toast.error(t('login.oidcRedirectFailed'));
    }
  };

  // Redirect if login page is disabled
  useEffect(() => {
    if (!stateLoaded) return;
    if (loginPageEnabled === false && authAdminRequest === false) {
      navigate('/', { replace: true });
    }
    if (authAdminRequest) {
      navigate('/register', { replace: true });
    }
  }, [stateLoaded, loginPageEnabled, authAdminRequest, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim()) {
      toast.error(t('login.usernameRequired'));
      return;
    }
    if (!password.trim()) {
      toast.error(t('login.passwordRequired'));
      return;
    }
    const success = await login(username, password);
    if (success) {
      navigate('/', { replace: true });
    } else {
      toast.error(t('login.invalidUsernameOrPassword'));
    }
  };

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden">
      {/* Soft gradient background - light blue & white tones */}
      <div className="absolute inset-0 bg-gradient-to-br from-blue-50 via-sky-100 to-blue-100 animate-gradient-shift" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(255,255,255,0.6),transparent_60%)]" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,rgba(219,234,254,0.4),transparent_50%)]" />

      {/* Floating orbs */}
      <div className="absolute top-1/4 left-1/4 h-64 w-64 rounded-full bg-blue-200/30 blur-3xl animate-float" />
      <div className="absolute bottom-1/4 right-1/4 h-80 w-80 rounded-full bg-sky-200/25 blur-3xl animate-float-delayed" />
      <div className="absolute top-1/2 right-1/3 h-48 w-48 rounded-full bg-blue-100/30 blur-3xl animate-float" />

      {/* Login Card */}
      <Card className="relative z-10 w-full max-w-4xl mx-4 overflow-hidden border-0 p-0 gap-0 shadow-2xl shadow-blue-200/40">
        <CardContent className="p-0">
          <div className="grid md:grid-cols-2">
            {/* Left - Branding */}
            <div className="flex flex-col justify-center p-8 md:p-12 bg-gradient-to-br from-blue-500 to-blue-600 relative overflow-hidden">
              {/* Decorative background circles */}
              <div className="absolute top-0 right-0 w-40 h-40 bg-white/10 rounded-full -translate-y-1/2 translate-x-1/2" />
              <div className="absolute bottom-0 left-0 w-32 h-32 bg-white/5 rounded-full translate-y-1/3 -translate-x-1/3" />
              <div className="relative z-10">
                <img
                  src={`${import.meta.env.BASE_URL}img/nacos-logo.svg`}
                  alt="Nacos"
                  className="h-10 mb-2"
                />
                <p className="text-xs text-white/60 mb-6">Dynamic Naming and Configuration Service</p>
                <p className="text-sm text-white/85 leading-relaxed mb-8">
                  {t('login.productDesc')}
                </p>
                {/* Decorative lines */}
                <div className="space-y-2">
                  <div className="h-1 w-16 rounded-full bg-gradient-to-r from-white/50 to-transparent" />
                  <div className="h-1 w-24 rounded-full bg-gradient-to-r from-white/35 to-transparent" />
                  <div className="h-1 w-12 rounded-full bg-gradient-to-r from-white/20 to-transparent" />
                </div>
              </div>
            </div>

              {/* Right - Form or Disabled notice or OIDC */}
            <div className="flex flex-col justify-center bg-white p-8 md:p-12">
              {stateLoaded && authSystemType === 'oidc' ? (
                <>
                  <h2 className="text-xl font-semibold text-gray-800 mb-1">{t('login.login')}</h2>
                  <p className="text-sm text-gray-500 mb-6">
                    {t('login.ssoLoginTip')}
                  </p>
                  <Button
                    type="button"
                    onClick={handleOidcLogin}
                    className={cn(
                      'w-full h-10 bg-gradient-to-r from-blue-500 to-blue-600 text-white font-medium shadow-md shadow-blue-500/25',
                      'hover:from-blue-600 hover:to-blue-700 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-blue-500/30 transition-all duration-200'
                    )}
                  >
                    <KeyRound size={16} className="mr-2" />
                    {t('login.signInWithSSO')}
                  </Button>
                </>
              ) : stateLoaded && !consoleUiEnable ? (
                <>
                  <div className="flex items-center gap-2 mb-4">
                    <span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-amber-100">
                      <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4 text-amber-600" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg>
                    </span>
                    <h2 className="text-xl font-semibold text-gray-800">{t('login.consoleClosed')}</h2>
                  </div>
                  {guideMsg && (
                    <div
                      className="text-sm text-gray-500 leading-relaxed [&_a]:text-blue-600 [&_a]:underline [&_a]:hover:text-blue-700 [&_code]:bg-gray-100 [&_code]:px-1.5 [&_code]:py-0.5 [&_code]:rounded [&_code]:text-gray-700 [&_code]:font-mono [&_code]:text-xs"
                      dangerouslySetInnerHTML={{ __html: guideMsg }}
                    />
                  )}
                </>
              ) : (
                <>
              <h2 className="text-xl font-semibold text-gray-800 mb-1">{t('login.login')}</h2>
              <p className="text-sm text-gray-500 mb-6">
                {t('login.internalSysTip1')}
              </p>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="username" className="text-gray-700">{t('login.pleaseInputUsername')}</Label>
                  <Input
                    id="username"
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder={t('login.pleaseInputUsername')}
                    autoComplete="username"
                    className="h-10 border-gray-200 focus:border-blue-400 focus:ring-blue-400"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="password" className="text-gray-700">{t('login.pleaseInputPassword')}</Label>
                  <div className="relative">
                    <Input
                      id="password"
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder={t('login.pleaseInputPassword')}
                      autoComplete="current-password"
                      className="h-10 pr-10 border-gray-200 focus:border-blue-400 focus:ring-blue-400"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600 transition-colors"
                    >
                      {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                    </button>
                  </div>
                </div>
                <Button
                  type="submit"
                  disabled={authLoading}
                  className={cn(
                    'w-full h-10 bg-gradient-to-r from-blue-500 to-blue-600 text-white font-medium shadow-md shadow-blue-500/25',
                    'hover:from-blue-600 hover:to-blue-700 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-blue-500/30 transition-all duration-200',
                    'disabled:opacity-50 disabled:hover:translate-y-0'
                  )}
                >
                  {authLoading ? (
                    <Loader2 size={16} className="animate-spin mr-2" />
                  ) : null}
                  {t('login.login')}
                </Button>
              </form>
                </>
              )}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* CSS for animations */}
      <style>{`
        @keyframes gradient-shift {
          0%, 100% { background-position: 0% 50%; }
          50% { background-position: 100% 50%; }
        }
        .animate-gradient-shift {
          background-size: 200% 200%;
          animation: gradient-shift 15s ease infinite;
        }
        @keyframes float {
          0%, 100% { transform: translateY(0) translateX(0); }
          50% { transform: translateY(-20px) translateX(10px); }
        }
        .animate-float {
          animation: float 8s ease-in-out infinite;
        }
        .animate-float-delayed {
          animation: float 10s ease-in-out 2s infinite;
        }
      `}</style>
    </div>
  );
}
