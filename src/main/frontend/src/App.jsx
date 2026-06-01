import { useState, useEffect } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { apiGet } from './api';

export default function App() {
  const [runtime, setRuntime] = useState(null);
  const [navOpen, setNavOpen] = useState(true);

  useEffect(() => {
    apiGet('/api/novel/config/frontend-runtime').then(setRuntime).catch(() => {});
  }, []);

  const navItems = [
    { to: '/', label: '总览', icon: '▣' },
    { to: '/characters', label: '角色', icon: '▤' },
    { to: '/pacing', label: '节奏', icon: '▥' },
    { to: '/foreshadowing', label: '伏笔', icon: '▦' },
    { to: '/files', label: '文件', icon: '▧' },
    { to: '/system', label: '系统', icon: '▨' },
  ];

  return (
    <div className="app-layout">
      <aside className={`sidebar ${navOpen ? 'open' : 'closed'}`}>
        <div className="sidebar-header">
          <h2 className="pixel-title">A-Agent</h2>
          <span className="pixel-subtitle">导演台 v2.0</span>
        </div>
        <nav>
          {navItems.map(item => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className="nav-item">
              <span className="nav-icon">{item.icon}</span>
              <span className="nav-label">{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-footer">
          {runtime && (
            <div className="runtime-info">
              <span className={`status-dot ${runtime.securityEnabled ? 'on' : 'off'}`} />
              <span>{runtime.securityEnabled ? 'JWT ON' : 'JWT OFF'}</span>
            </div>
          )}
          <button className="pixel-btn small" onClick={() => setNavOpen(!navOpen)}>
            {navOpen ? '◀' : '▶'}
          </button>
        </div>
      </aside>
      <main className="main-content">
        <Outlet context={{ runtime }} />
      </main>
    </div>
  );
}
