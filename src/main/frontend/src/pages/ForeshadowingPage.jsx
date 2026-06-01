import { useState, useEffect } from 'react';
import { apiGet } from '../api';

const URGENCY_COLORS = { critical: '#e94560', high: '#f5a623', medium: '#45b7d1', low: '#666' };
const STATUS_LABELS = { PLANTED: '已埋', REMINDED: '已提醒', PAID_OFF: '已回收', ABANDONED: '废弃' };

export default function ForeshadowingPage() {
  const [novels, setNovels] = useState([]);
  const [selectedNovel, setSelectedNovel] = useState(null);
  const [ganttData, setGanttData] = useState([]);
  const [loops, setLoops] = useState([]);

  useEffect(() => {
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list);
    }).catch(() => {});
  }, []);

  const loadData = (novel) => {
    setSelectedNovel(novel);
    apiGet(`/api/novel/${novel.id}/foreshadowing/gantt`).then(d => {
      const items = Array.isArray(d) ? d : (d?.data || []);
      setGanttData(items);
    }).catch(() => setGanttData([]));
    apiGet(`/api/novel/${novel.id}/foreshadowing`).then(d => {
      setLoops(Array.isArray(d) ? d : (d?.data || []));
    }).catch(() => setLoops([]));
  };

  const maxChapter = ganttData.reduce((max, item) => {
    const end = item.payoffChapter || item.deadlineChapter || item.plantedChapter || 0;
    return Math.max(max, end);
  }, 0) + 10;

  return (
    <div>
      <div className="page-header">
        <h2>▦ 伏笔 FORESHADOWING</h2>
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
          <div className="pixel-card">
            <h3>伏笔甘特图</h3>
            {ganttData.length === 0 ? (
              <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>暂无伏笔数据</p>
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <div style={{ minWidth: 600 }}>
                  {ganttData.slice(0, 20).map((item, idx) => (
                    <div key={idx} style={{ display: 'flex', alignItems: 'center', marginBottom: 8, gap: 12 }}>
                      <div style={{ width: 100, fontSize: 11, textAlign: 'right', flexShrink: 0 }}>
                        <span className={`badge badge-${item.urgency === 'critical' ? 'critical' : item.urgency === 'high' ? 'warning' : item.urgency === 'medium' ? 'constellation' : ''}`}
                              style={!['critical','high','medium'].includes(item.urgency) ? {} : {}}>
                          {item.urgency || 'low'}
                        </span>
                      </div>
                      <div style={{ flex: 1, position: 'relative', height: 28 }}>
                        <div style={{
                          position: 'absolute',
                          left: `${(item.plantedChapter || 0) / maxChapter * 100}%`,
                          width: `${Math.max(2, ((item.payoffChapter || item.deadlineChapter || item.plantedChapter + 5) - (item.plantedChapter || 0)) / maxChapter * 100)}%`,
                          height: '100%',
                          background: item.status === 'PAID_OFF' ? '#16c79a' : (URGENCY_COLORS[item.urgency] || '#666'),
                          opacity: item.status === 'PAID_OFF' ? 0.6 : 1,
                          display: 'flex',
                          alignItems: 'center',
                          paddingLeft: 6,
                          fontSize: 10,
                          overflow: 'hidden',
                          whiteSpace: 'nowrap'
                        }}>
                          {item.content || `Ch${item.plantedChapter}`}
                        </div>
                      </div>
                      <div style={{ width: 60, fontSize: 10, color: 'var(--text-dim)', flexShrink: 0 }}>
                        →{item.payoffChapter ? 'Ch' + item.payoffChapter : '...'}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>

          <div className="pixel-card">
            <h3>伏笔列表</h3>
            {loops.length === 0 ? (
              <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>暂无伏笔</p>
            ) : (
              <table className="pixel-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>内容</th>
                    <th>类型</th>
                    <th>埋设章</th>
                    <th>状态</th>
                    <th>紧急度</th>
                  </tr>
                </thead>
                <tbody>
                  {loops.map(l => (
                    <tr key={l.id}>
                      <td>{l.id}</td>
                      <td style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                        {l.content || '-'}
                      </td>
                      <td><span className="badge badge-constellation">{l.loopType || '-'}</span></td>
                      <td>Ch{l.plantedChapter || '-'}</td>
                      <td>{STATUS_LABELS[l.status] || l.status}</td>
                      <td>
                        <span className={`badge badge-${l.urgency === 'critical' ? 'critical' : l.urgency === 'high' ? 'warning' : l.urgency === 'medium' ? 'constellation' : 'success'}`}>
                          {l.urgency || 'low'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </>
      )}
    </div>
  );
}
