import { useState, useEffect } from 'react';
import { apiGet } from '../api';

export default function FilesPage() {
  const [novels, setNovels] = useState([]);
  const [selectedNovel, setSelectedNovel] = useState(null);
  const [chapters, setChapters] = useState([]);
  const [outline, setOutline] = useState('');
  const [tab, setTab] = useState('chapters');

  useEffect(() => {
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list);
    }).catch(() => {});
  }, []);

  const loadData = (novel) => {
    setSelectedNovel(novel);
    apiGet(`/api/novel/${novel.id}/chapters`).then(d => setChapters(Array.isArray(d) ? d : (d?.data || []))).catch(() => setChapters([]));
    apiGet(`/api/novel/${novel.id}/outline`).then(d => setOutline(typeof d === 'string' ? d : (d?.description || d?.data?.description || ''))).catch(() => setOutline(''));
  };

  return (
    <div>
      <div className="page-header">
        <h2>▧ 文件 FILES</h2>
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
        <div className="pixel-card">
          <div style={{ display: 'flex', gap: 12, marginBottom: 16 }}>
            <button className={`pixel-btn small ${tab === 'chapters' ? '' : ''}`}
                    style={tab === 'chapters' ? { borderColor: 'var(--accent-bright)' } : {}}
                    onClick={() => setTab('chapters')}>章节列表</button>
            <button className={`pixel-btn small ${tab === 'outline' ? '' : ''}`}
                    style={tab === 'outline' ? { borderColor: 'var(--accent-bright)' } : {}}
                    onClick={() => setTab('outline')}>大纲</button>
            <button className={`pixel-btn small ${tab === 'export' ? '' : ''}`}
                    style={tab === 'export' ? { borderColor: 'var(--accent-bright)' } : {}}
                    onClick={() => setTab('export')}>导出</button>
          </div>

          {tab === 'chapters' && (
            chapters.length === 0 ? (
              <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>暂无章节</p>
            ) : (
              <table className="pixel-table">
                <thead>
                  <tr>
                    <th>章节号</th>
                    <th>标题</th>
                    <th>字数</th>
                    <th>状态</th>
                    <th>Strand</th>
                  </tr>
                </thead>
                <tbody>
                  {chapters.map(c => (
                    <tr key={c.chapterNumber}>
                      <td>第{c.chapterNumber}章</td>
                      <td>{c.title || '-'}</td>
                      <td>{c.content ? c.content.length : 0}</td>
                      <td><span className={`badge ${c.writeState === 'READY' ? 'badge-success' : 'badge-warning'}`}>{c.writeState || '-'}</span></td>
                      <td><span className="badge badge-quest">{c.dominantStrand || '-'}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )
          )}

          {tab === 'outline' && (
            <div style={{ whiteSpace: 'pre-wrap', fontFamily: 'var(--font-mono)', fontSize: 13,
                          maxHeight: 500, overflow: 'auto', background: 'var(--bg)', padding: 16,
                          border: '2px solid var(--border-dim)' }}>
              {outline || '暂无大纲数据'}
            </div>
          )}

          {tab === 'export' && (
            <div style={{ textAlign: 'center', padding: 24 }}>
              <p style={{ marginBottom: 16 }}>导出全部章节为纯文本</p>
              <a href={`/api/novel/${selectedNovel.id}/export`} className="pixel-btn" target="_blank" rel="noreferrer">
                下载导出
              </a>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
