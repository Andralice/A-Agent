import { useState, useEffect } from 'react';
import { apiGet } from '../api';

export default function PacingPage() {
  const [novels, setNovels] = useState([]);
  const [selectedNovel, setSelectedNovel] = useState(null);
  const [strandStats, setStrandStats] = useState(null);
  const [readingPower, setReadingPower] = useState(null);

  useEffect(() => {
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list);
    }).catch(() => {});
  }, []);

  const loadData = (novel) => {
    setSelectedNovel(novel);

    apiGet(`/api/novel/${novel.id}/strand-stats`).then(setStrandStats).catch(() => setStrandStats({ questCount: 0, fireCount: 0, constellationCount: 0 }));
    apiGet(`/api/novel/${novel.id}/reading-power/trend`).then(d => setReadingPower(Array.isArray(d) ? d : (d?.data || []))).catch(() => setReadingPower([]));
  };

  const stats = strandStats || {};
  const totalStrand = (stats.questCount || 0) + (stats.fireCount || 0) + (stats.constellationCount || 0) || 1;

  return (
    <div>
      <div className="page-header">
        <h2>▥ 节奏 PACING</h2>
      </div>

      <div className="pixel-card">
        <h3>选择小说</h3>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {novels.map(n => (
            <button key={n.id} className="pixel-btn small"
                    style={selectedNovel?.id === n.id ? { borderColor: 'var(--accent-bright)', background: 'var(--accent)' } : {}}
                    onClick={() => loadData(n)}>
              #{n.id} {n.title}
            </button>
          ))}
        </div>
      </div>

      {selectedNovel && (
        <>
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-value badge-quest">{String(Math.round((stats.questCount || 0) * 100 / totalStrand))}%</div>
              <div className="stat-label">主线 QUEST</div>
            </div>
            <div className="stat-card">
              <div className="stat-value" style={{ color: '#ff6b9d' }}>{String(Math.round((stats.fireCount || 0) * 100 / totalStrand))}%</div>
              <div className="stat-label">感情 FIRE</div>
            </div>
            <div className="stat-card">
              <div className="stat-value" style={{ color: '#45b7d1' }}>{String(Math.round((stats.constellationCount || 0) * 100 / totalStrand))}%</div>
              <div className="stat-label">世界观 CONSTELLATION</div>
            </div>
            <div className="stat-card">
              <div className="stat-value">{stats.currentDominant || '-'}</div>
              <div className="stat-label">当前主导</div>
            </div>
          </div>

          <div className="pixel-card">
            <h3>Strand 分布</h3>
            <div style={{ display: 'flex', height: 40, background: 'var(--bg)', border: '2px solid var(--border-dim)' }}>
              <div style={{
                width: `${Math.round((stats.questCount || 0) * 100 / totalStrand)}%`,
                background: 'var(--accent-bright)', transition: 'width 0.3s'
              }} title={`主线 ${stats.questCount || 0}章`} />
              <div style={{
                width: `${Math.round((stats.fireCount || 0) * 100 / totalStrand)}%`,
                background: '#ff6b9d', transition: 'width 0.3s'
              }} title={`感情 ${stats.fireCount || 0}章`} />
              <div style={{
                width: `${Math.round((stats.constellationCount || 0) * 100 / totalStrand)}%`,
                background: '#45b7d1', transition: 'width 0.3s'
              }} title={`世界观 ${stats.constellationCount || 0}章`} />
            </div>
            <div style={{ display: 'flex', gap: 16, marginTop: 8, fontSize: 10, color: 'var(--text-dim)' }}>
              <span>■ 主线({stats.questCount || 0})</span>
              <span style={{ color: '#ff6b9d' }}>■ 感情({stats.fireCount || 0})</span>
              <span style={{ color: '#45b7d1' }}>■ 世界观({stats.constellationCount || 0})</span>
            </div>
            {stats.suggestion && (
              <p style={{ marginTop: 12, fontSize: 12, color: stats.warning ? 'var(--warning)' : 'var(--text-dim)' }}>
                {stats.warning ? '⚠ ' : 'ℹ '}{stats.suggestion}
              </p>
            )}
          </div>

          <div className="pixel-card">
            <h3>阅读力趋势</h3>
            {readingPower && readingPower.length > 0 ? (
              <table className="pixel-table">
                <thead>
                  <tr>
                    <th>章</th>
                    <th>钩子类型</th>
                    <th>强度</th>
                    <th>爽点模式</th>
                    <th>微兑现</th>
                    <th>违规</th>
                  </tr>
                </thead>
                <tbody>
                  {readingPower.slice(-20).map(r => (
                    <tr key={r.chapterNumber}>
                      <td>Ch{r.chapterNumber}</td>
                      <td><span className="badge badge-constellation">{r.hookType || '-'}</span></td>
                      <td><span className={`badge badge-${r.hookStrength || 'weak'}`}>{r.hookStrength || '-'}</span></td>
                      <td>{r.coolPointPattern || '-'}</td>
                      <td>{r.microPayoffCount || 0}</td>
                      <td>{r.hasHardViolations ? <span className="badge badge-critical">!</span> : <span className="badge badge-success">OK</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>暂无阅读力数据</p>}
          </div>
        </>
      )}
    </div>
  );
}
