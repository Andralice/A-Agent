import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom/client';
import { HashRouter, Routes, Route } from 'react-router-dom';
import App from './App';
import './index.css';

const OverviewPage = lazy(() => import('./pages/OverviewPage'));
const CharactersPage = lazy(() => import('./pages/CharactersPage'));
const PacingPage = lazy(() => import('./pages/PacingPage'));
const ForeshadowingPage = lazy(() => import('./pages/ForeshadowingPage'));
const FilesPage = lazy(() => import('./pages/FilesPage'));
const SystemPage = lazy(() => import('./pages/SystemPage'));

function Loading() {
  return <div className="loading-screen">LOADING...</div>;
}

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <HashRouter>
    <Routes>
      <Route path="/" element={<App />}>
        <Route index element={<Suspense fallback={<Loading />}><OverviewPage /></Suspense>} />
        <Route path="characters" element={<Suspense fallback={<Loading />}><CharactersPage /></Suspense>} />
        <Route path="pacing" element={<Suspense fallback={<Loading />}><PacingPage /></Suspense>} />
        <Route path="foreshadowing" element={<Suspense fallback={<Loading />}><ForeshadowingPage /></Suspense>} />
        <Route path="files" element={<Suspense fallback={<Loading />}><FilesPage /></Suspense>} />
        <Route path="system" element={<Suspense fallback={<Loading />}><SystemPage /></Suspense>} />
      </Route>
    </Routes>
  </HashRouter>
);
