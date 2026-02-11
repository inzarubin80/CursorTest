import { useState, useEffect } from 'react';
import './NoteEditor.css';

/**
 * @param {{ note: import('../types/note').Note, onSave: (title: string, body: string) => void }}
 */
export default function NoteEditor({ note, onSave }) {
  const [title, setTitle] = useState(note.title);
  const [body, setBody] = useState(note.body);

  useEffect(() => {
    setTitle(note.title);
    setBody(note.body);
  }, [note.id, note.title, note.body]);

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave(title, body);
  };

  return (
    <form className="note-editor" onSubmit={handleSubmit}>
      <div className="note-editor-header">
        <input
          type="text"
          className="note-editor-title-input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Заголовок"
          autoFocus
        />
        <button type="submit">Сохранить</button>
      </div>
      <textarea
        className="note-editor-body"
        value={body}
        onChange={(e) => setBody(e.target.value)}
        placeholder="Текст заметки"
        rows={20}
      />
    </form>
  );
}
