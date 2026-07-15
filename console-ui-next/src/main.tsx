import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './monaco-setup';
import './globals.css';
import './locales';
import App from './App';

// Handle OIDC Cookie-based authentication (cluster-friendly, no server-side storage)
(function oidcCookieSync() {
  const hash = window.location.hash;

  // Handle error response from OIDC callback
  if (hash && hash.includes('error=')) {
    try {
      const queryString = hash.split('?')[1];
      if (queryString) {
        const params = new URLSearchParams(queryString);
        const error = params.get('error');
        if (error) {
          console.error('[OIDC] Authentication failed:', decodeURIComponent(error));
          sessionStorage.setItem('oidcError', decodeURIComponent(error));
          const newUrl = window.location.href.split('#')[0] + '#/login';
          window.history.replaceState(null, '', newUrl);
        }
      }
    } catch (e) {
      console.error('[OIDC] Failed to parse error from URL', e);
    }
    return;
  }

  function getCookie(name: string): string | null {
    const value = `; ${document.cookie}`;
    const parts = value.split(`; ${name}=`);
    if (parts.length === 2) return parts.pop()!.split(';').shift() || null;
    return null;
  }

  function deleteCookie(name: string) {
    document.cookie = name + '=; Path=/; Expires=Thu, 01 Jan 1970 00:00:01 GMT;';
  }

  const accessToken = getCookie('accessToken');
  const username = getCookie('username');

  if (accessToken && username) {
    localStorage.setItem('token', JSON.stringify({
      accessToken,
      username: decodeURIComponent(username),
      globalAdmin: false,
      oidc: true,
    }));
    deleteCookie('accessToken');
    deleteCookie('username');
  }
})();

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
