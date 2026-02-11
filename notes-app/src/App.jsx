import { useState, useMemo, useCallback } from 'react';
import { getNotes, saveNotes, createNote } from './storage/notesStorage';
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
  }, [selectedId, notes, persistNotes]);

  return (
    <div className="app">
      <aside className="app-sidebar">
        <div className="app-sidebar-header">
          <h2 className="app-sidebar-title">Заметки</h2>
          <button type="button" className="app-new-note" onClick={handleCreate}>
            Новая заметка
          </button>
        </div>
        <SearchBar value={searchQuery} onChange={setSearchQuery} />
        {notes.length === 0 ? (
          <EmptyState onCreateClick={handleCreate} />
        ) : (
          <NoteList
            notes={filteredNotes}
            selectedId={selectedId}
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
          <NoteEditor note={selectedNote} onSave={handleSave} />
        )}
        {selectedNote && editingId !== selectedNote.id && (
          <NoteView
            note={selectedNote}
            onEdit={handleEdit}
            onDelete={handleDelete}
          />
        )}
      </main>
    </div>
  );
}

export default App;
