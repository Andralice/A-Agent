import { useState, useEffect } from 'react';
import { apiGet } from '../api';

export default function CharactersPage() {
  const [novels, setNovels] = useState([]);
  const [selectedNovel, setSelectedNovel] = useState(null);
  const [characters, setCharacters] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    apiGet('/api/novel/list').then(data => {
      const list = Array.isArray(data) ? data : (data?.data || []);
      setNovels(list);
    }).catch(() => {});
  }, []);

  const loadCharacters = (novelId) => {
    setLoading(true);
    apiGet(`/api/novel/${novelId}/characters`)
      .then(data => setCharacters(Array.isArray(data) ? data : (data?.data || [])))
      .catch(() => setCharacters([]))
      .finally(() => setLoading(false));
  };

  const handleSelect = (novel) => {
    setSelectedNovel(novel);
    loadCharacters(novel.id);
  };

  return (
    <div>
      <div className="page-header">
        <h2>▤ 角色 CHARACTERS</h2>
      </div>

      <div className="pixel-card">
        <h3>选择小说</h3>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          {novels.map(n => (
            <button key={n.id} className={`pixel-btn small ${selectedNovel?.id === n.id ? '' : ''}`}
                    style={selectedNovel?.id === n.id ? { borderColor: 'var(--accent-bright)', background: 'var(--accent)' } : {}}
                    onClick={() => handleSelect(n)}>
              #{n.id} {n.title}
            </button>
          ))}
        </div>
      </div>

      {selectedNovel && (
        <div className="pixel-card">
          <h3>角色列表 — {selectedNovel.title}</h3>
          {loading ? <div className="loading-screen">LOADING...</div> : (
            characters.length === 0 ? (
              <p style={{ color: 'var(--text-dim)', textAlign: 'center', padding: 24 }}>暂无角色数据</p>
            ) : (
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))', gap: 16 }}>
                {characters.map(c => (
                  <div key={c.id} className="pixel-card" style={{ margin: 0 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                      <strong>{c.characterName}</strong>
                      <span className="badge badge-constellation">{c.characterType || '未分类'}</span>
                    </div>
                    <p style={{ fontSize: 12, color: 'var(--text-dim)', maxHeight: 120, overflow: 'hidden' }}>
                      {(c.profileContent || '').substring(0, 300)}
                    </p>
                  </div>
                ))}
              </div>
            )
          )}
        </div>
      )}
    </div>
  );
}
