import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './monaco-setup';
import './globals.css';
import './locales';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
