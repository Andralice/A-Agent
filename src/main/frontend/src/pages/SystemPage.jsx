import { useState, useEffect } from 'react';
import { apiGet, apiPost } from '../api';

export default function SystemPage() {
  const [runtime, setRuntime] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [selectedNovel, setSelectedNovel] = useState(null);
  const [novels, setNovels] = useState([]);
  const [loginUser, setLoginUser] = useState('');
  const [loginPass, setLoginPass] = useState('');
  const [auth, setAuth] = useState(null);

  useEffect(() => {
    apiGet('/api/novel/config/frontend-runtime').then(setRuntime).catch(() => {});
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list);
    }).catch(() => {});
    apiGet('/api/auth/me').then(setAuth).catch(() => setAuth(null));
  }, []);

  const loadTasks = (novel) => {
    setSelectedNovel(novel);
    apiGet(`/api/novel/${novel.id}/generation-tasks`).then(d => {
      setTasks(Array.isArray(d) ? d : (d?.data || []));
    }).catch(() => setTasks([]));
  };

  const handleLogin = async () => {
    try {
      const data = await apiPost('/api/auth/login', { username: loginUser, password: loginPass });
      if (data.token) {
        localStorage.setItem('token', data.token);
        setAuth({ authenticated: true, username: loginUser, role: data.role });
      }
    } catch (e) {
      alert('登录失败');
    }
  };

  const handleTaskAction = (taskId, action) => {
    apiPost(`/api/novel/tasks/${taskId}/${action}`, {}).then(() => {
      if (selectedNovel) loadTasks(selectedNovel);
    }).catch(e => alert(`${action} 失败: ${e.message}`));
  };

  return (
    <div>
      <div className="page-header">
        <h2>▨ 系统 SYSTEM</h2>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{runtime?.securityEnabled ? 'ON' : 'OFF'}</div>
          <div className="stat-label">JWT 安全</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{runtime?.narrativeEngineEnabled ? 'ON' : 'OFF'}</div>
          <div className="stat-label">叙事引擎</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{runtime?.m7ArtifactEnabled ? 'ON' : 'OFF'}</div>
          <div className="stat-label">M7 侧车</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{runtime?.m9CrosscutEnabled ? 'ON' : 'OFF'}</div>
          <div className="stat-label">M9 跨章</div>
        </div>
      </div>

      {(!auth?.authenticated) && (
        <div className="pixel-card">
          <h3>登录</h3>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end' }}>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label>用户名</label>
              <input className="pixel-input" value={loginUser} onChange={e => setLoginUser(e.target.value)}
                     style={{ width: 150 }} placeholder="admin" />
            </div>
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label>密码</label>
              <input className="pixel-input" type="password" value={loginPass} onChange={e => setLoginPass(e.target.value)}
                     style={{ width: 200 }} />
            </div>
            <button className="pixel-btn" onClick={handleLogin}>登录</button>
          </div>
        </div>
      )}

      <div className="pixel-card">
        <h3>生成任务管理</h3>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 16 }}>
          {novels.map(n => (
            <button key={n.id} className="pixel-btn small"
                    style={selectedNovel?.id === n.id ? { borderColor: 'var(--accent-bright)', background: 'var(--accent)' } : {}}
                    onClick={() => loadTasks(n)}>
              #{n.id} {n.title}
            </button>
          ))}
        </div>

        {tasks.length === 0 ? (
          <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>
            {selectedNovel ? '暂无生成任务' : '请选择小说查看任务'}
          </p>
        ) : (
          <table className="pixel-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>类型</th>
                <th>状态</th>
                <th>章节区间</th>
                <th>重试</th>
                <th>错误</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {tasks.map(t => (
                <tr key={t.id}>
                  <td>{t.id}</td>
                  <td><span className="badge badge-constellation">{t.taskType}</span></td>
                  <td>
                    <span className={`badge ${t.status === 'DONE' ? 'badge-success' : t.status === 'FAILED' ? 'badge-critical' : t.status === 'RUNNING' ? 'badge-quest' : 'badge-warning'}`}>
                      {t.status}
                    </span>
                  </td>
                  <td>{t.rangeFrom || '-'} - {t.rangeTo || '-'}</td>
                  <td>{t.retryCount || 0}</td>
                  <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 11 }}>
                    {t.lastError || '-'}
                  </td>
                  <td style={{ display: 'flex', gap: 4 }}>
                    {t.status === 'PENDING' && <button className="pixel-btn small" onClick={() => handleTaskAction(t.id, 'kick')}>启动</button>}
                    {(t.status === 'RUNNING' || t.status === 'PENDING') && <button className="pixel-btn small danger" onClick={() => handleTaskAction(t.id, 'cancel')}>取消</button>}
                    {t.status === 'FAILED' && <button className="pixel-btn small" onClick={() => handleTaskAction(t.id, 'retry')}>重试</button>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
