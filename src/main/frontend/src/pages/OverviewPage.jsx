import { useState, useEffect } from 'react';
import { apiGet } from '../api';

export default function OverviewPage() {
  const [novels, setNovels] = useState([]);
  const [stats, setStats] = useState({ novelCount: 0, chapterCount: 0, avgScore: 0 });

  useEffect(() => {
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list.slice(0, 20));
      setStats({ novelCount: list.length, chapterCount: 0, avgScore: 0 });
    }).catch(() => {});
  }, []);

  return (
    <div>
      <div className="page-header">
        <h2>▣ 总览 OVERVIEW</h2>
      </div>

      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{stats.novelCount}</div>
          <div className="stat-label">NOVELS</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{stats.chapterCount}</div>
          <div className="stat-label">CHAPTERS</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">--</div>
          <div className="stat-label">AVG SCORE</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">--</div>
          <div className="stat-label">WORDS</div>
        </div>
      </div>

      <div className="pixel-card">
        <h3>小说列表</h3>
        <table className="pixel-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>标题</th>
              <th>题材</th>
              <th>流水线</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {novels.map(n => (
              <tr key={n.id}>
                <td>{n.id}</td>
                <td>{n.title || '未命名'}</td>
                <td>{n.topic || '-'}</td>
                <td><span className="badge badge-quest">{n.writingPipeline || '-'}</span></td>
                <td><span className="badge badge-success">{n.writePhase || 'IDLE'}</span></td>
                <td>
                  <a href={`/novels.html?id=${n.id}`} className="pixel-btn small">打开</a>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {novels.length === 0 && <p style={{ padding: 24, textAlign: 'center', color: 'var(--text-dim)' }}>暂无小说数据</p>}
      </div>
    </div>
  );
}
