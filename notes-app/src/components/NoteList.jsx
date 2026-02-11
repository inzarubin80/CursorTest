import './NoteList.css';

/**
 * @param {{ notes: import('../types/note').Note[], selectedId: string | null, onSelect: (id: string) => void }}
 */
export default function NoteList({ notes, selectedId, onSelect }) {
  const sorted = [...notes].sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));

  return (
    <ul className="note-list">
      {sorted.map((note) => (
        <li
          key={note.id}
          className={`note-list-item ${selectedId === note.id ? 'selected' : ''}`}
          onClick={() => onSelect(note.id)}
        >
          <span className="note-list-item-title">{note.title || '(Без названия)'}</span>
          <span className="note-list-item-preview">
            {note.body ? note.body.slice(0, 60) + (note.body.length > 60 ? '…' : '') : ''}
          </span>
          <span className="note-list-item-date">
            {note.createdAt ? new Date(note.createdAt).toLocaleDateString() : ''}
          </span>
        </li>
      ))}
    </ul>
  );
}
