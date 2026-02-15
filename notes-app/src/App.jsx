import { useState, useMemo, useCallback, useRef, useEffect } from 'react';
import { getNotes, saveNotes, createNote, getCurrentWork, setCurrentWork, downloadExportJson, parseImportJson } from './storage/notesStorage';
import NoteList from './components/NoteList';
import EmptyState from './components/EmptyState';
import NoteView from './components/NoteView';
import NoteEditor from './components/NoteEditor';
import SearchBar from './components/SearchBar';
import './App.css';

function loadNotes() {
  const stored = getNotes();
  if (Array.isArray(stored) && stored.length > 0) return stored;
  return [];
}

function App() {
  const [notes, setNotes] = useState(loadNotes);
  const [selectedId, setSelectedId] = useState(null);
  const [editingId, setEditingId] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [currentWork, setCurrentWorkState] = useState(getCurrentWork);
  const [jsonMenuOpen, setJsonMenuOpen] = useState(false);
  const fileInputRef = useRef(null);
  const jsonMenuRef = useRef(null);

  useEffect(() => {
    if (!jsonMenuOpen) return;
    const onDocClick = (e) => {
      if (jsonMenuRef.current?.contains(e.target)) return;
      setJsonMenuOpen(false);
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [jsonMenuOpen]);

  const filteredNotes = useMemo(() => {
    if (!searchQuery.trim()) return notes;
    const q = searchQuery.trim().toLowerCase();
    return notes.filter(
      (n) =>
        (n.title && n.title.toLowerCase().includes(q)) ||
        (n.body && n.body.toLowerCase().includes(q))
    );
  }, [notes, searchQuery]);

  const selectedNote = selectedId ? notes.find((n) => n.id === selectedId) : null;

  const persistNotes = useCallback((nextNotes) => {
    setNotes(nextNotes);
    saveNotes(nextNotes);
  }, []);

  const handleCreate = useCallback(() => {
    const note = createNote({ title: '', body: '' });
    const nextNotes = [note, ...notes];
    persistNotes(nextNotes);
    setSelectedId(note.id);
    setEditingId(note.id);
  }, [notes, persistNotes]);

  const handleSelect = useCallback((id) => {
    setSelectedId(id);
    setEditingId(null);
  }, []);

  const handleEdit = useCallback(() => {
    if (selectedId) setEditingId(selectedId);
  }, [selectedId]);

  const handleSave = useCallback(
    (title, body) => {
      if (!selectedId) return;
      const nextNotes = notes.map((n) =>
        n.id === selectedId
          ? { ...n, title, body, updatedAt: Date.now() }
          : n
      );
      persistNotes(nextNotes);
      setEditingId(null);
    },
    [selectedId, notes, persistNotes]
  );

  const handleDelete = useCallback(() => {
    if (!selectedId) return;
    if (!window.confirm('Удалить эту заметку?')) return;
    const nextNotes = notes.filter((n) => n.id !== selectedId);
    persistNotes(nextNotes);
    setSelectedId(null);
    setEditingId(null);
    if (currentWork.noteId === selectedId) {
      setCurrentWork({ noteId: null, startedAt: null });
      setCurrentWorkState({ noteId: null, startedAt: null });
    }
  }, [selectedId, notes, persistNotes, currentWork.noteId]);

  const handleTakeToWork = useCallback((noteId) => {
    setCurrentWork({ noteId, startedAt: null });
    setCurrentWorkState({ noteId, startedAt: null });
  }, []);

  const handleStartWork = useCallback(() => {
    if (!currentWork.noteId) return;
    const startedAt = Date.now();
    setCurrentWork({ noteId: currentWork.noteId, startedAt });
    setCurrentWorkState({ noteId: currentWork.noteId, startedAt });
  }, [currentWork.noteId]);

  const handleEndWork = useCallback(
    (whatDone) => {
      if (!currentWork.noteId || currentWork.startedAt == null) return;
      const endedAt = Date.now();
      const session = {
        startedAt: currentWork.startedAt,
        endedAt,
        whatDone: whatDone?.trim() || undefined,
      };
      const nextNotes = notes.map((n) =>
        n.id === currentWork.noteId
          ? {
              ...n,
              workSessions: [...(n.workSessions || []), session],
              updatedAt: endedAt,
            }
          : n
      );
      persistNotes(nextNotes);
      setCurrentWork({ noteId: null, startedAt: null });
      setCurrentWorkState({ noteId: null, startedAt: null });
    },
    [currentWork, notes, persistNotes]
  );

  const handleToggleCompleted = useCallback(
    (noteId) => {
      const nextNotes = notes.map((n) =>
        n.id === noteId ? { ...n, completed: !n.completed, updatedAt: Date.now() } : n
      );
      persistNotes(nextNotes);
    },
    [notes, persistNotes]
  );

  const handleUpdateWorkSession = useCallback(
    (noteId, sessionIndex, payload) => {
      const nextNotes = notes.map((n) => {
        if (n.id !== noteId || !n.workSessions?.length || sessionIndex < 0 || sessionIndex >= n.workSessions.length)
          return n;
        const nextSessions = [...n.workSessions];
        nextSessions[sessionIndex] = { ...nextSessions[sessionIndex], ...payload };
        return { ...n, workSessions: nextSessions, updatedAt: Date.now() };
      });
      persistNotes(nextNotes);
    },
    [notes, persistNotes]
  );

  const handleExportJson = useCallback(() => {
    setJsonMenuOpen(false);
    downloadExportJson();
  }, []);

  const handleOpenImport = useCallback(() => {
    setJsonMenuOpen(false);
    fileInputRef.current?.click();
  }, []);

  const handleImportJson = useCallback(
    (e) => {
      const file = e.target.files?.[0];
      if (!file) return;
      const reader = new FileReader();
      reader.onload = () => {
        try {
          const { notes: nextNotes, currentWork: nextWork } = parseImportJson(
            String(reader.result)
          );
          if (notes.length > 0 && !window.confirm('Заменить текущие заметки?')) {
            e.target.value = '';
            return;
          }
          persistNotes(nextNotes);
          setSelectedId(null);
          setEditingId(null);
          const noteIds = new Set(nextNotes.map((n) => n.id));
          if (nextWork?.noteId != null && noteIds.has(nextWork.noteId)) {
            setCurrentWork(nextWork);
            setCurrentWorkState(nextWork);
          } else {
            setCurrentWork({ noteId: null, startedAt: null });
            setCurrentWorkState({ noteId: null, startedAt: null });
          }
        } catch (err) {
          window.alert(err instanceof Error ? err.message : 'Ошибка чтения файла');
        }
        e.target.value = '';
      };
      reader.readAsText(file);
    },
    [notes.length, persistNotes]
  );

  return (
    <div className="app">
      <aside className="app-sidebar">
        <div className="app-sidebar-header">
          <div className="app-sidebar-title-row">
            <h2 className="app-sidebar-title">Заметки</h2>
            <div className="app-json-menu-wrap" ref={jsonMenuRef}>
              <button
                type="button"
                className="app-json-menu-trigger"
                onClick={() => setJsonMenuOpen((v) => !v)}
                title="Данные JSON"
                aria-expanded={jsonMenuOpen}
                aria-haspopup="true"
              >
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                  <circle cx="12" cy="12" r="1" />
                  <circle cx="12" cy="5" r="1" />
                  <circle cx="12" cy="19" r="1" />
                </svg>
              </button>
              {jsonMenuOpen && (
                <div className="app-json-menu">
                  <button type="button" className="app-json-menu-item" onClick={handleExportJson}>
                    Выгрузить в JSON
                  </button>
                  <button type="button" className="app-json-menu-item" onClick={handleOpenImport}>
                    Загрузить из JSON
                  </button>
                </div>
              )}
              <input
                ref={fileInputRef}
                type="file"
                accept=".json"
                aria-hidden="true"
                tabIndex={-1}
                style={{ position: 'absolute', width: 0, height: 0, opacity: 0 }}
                onChange={handleImportJson}
              />
            </div>
          </div>
          <button type="button" className="app-new-note" onClick={handleCreate}>
            Новая заметка
          </button>
        </div>
        <SearchBar value={searchQuery} onChange={setSearchQuery} />
        {notes.length === 0 ? (
          <EmptyState />
        ) : (
          <NoteList
            notes={filteredNotes}
            selectedId={selectedId}
            currentWorkNoteId={currentWork.noteId}
            onSelect={handleSelect}
          />
        )}
      </aside>
      <main className="app-main">
        {!selectedNote && notes.length > 0 && (
          <div className="app-placeholder">Выберите заметку или создайте новую.</div>
        )}
        {!selectedNote && notes.length === 0 && (
          <div className="app-placeholder">Создайте первую заметку.</div>
        )}
        {selectedNote && editingId === selectedNote.id && (
          <NoteEditor key={selectedNote.id} note={selectedNote} onSave={handleSave} />
        )}
        {selectedNote && editingId !== selectedNote.id && (
          <NoteView
            note={selectedNote}
            currentWork={currentWork}
            onEdit={handleEdit}
            onDelete={handleDelete}
            onTakeToWork={handleTakeToWork}
            onStartWork={handleStartWork}
            onEndWork={handleEndWork}
            onToggleCompleted={handleToggleCompleted}
            onUpdateWorkSession={handleUpdateWorkSession}
          />
        )}
      </main>
    </div>
  );
}

export default App;
