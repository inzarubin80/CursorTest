import './NoteList.css';

/**
 * @param {{
 *   notes: import('../types/note').Note[],
 *   selectedId: string | null,
 *   currentWorkNoteId: string | null,
 *   onSelect: (id: string) => void,
 * }}
 */
export default function NoteList({ notes, selectedId, currentWorkNoteId, onSelect }) {
  const sorted = [...notes].sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0));

  return (
    <ul className="note-list">
      {sorted.map((note) => {
        const isInWork = currentWorkNoteId === note.id;
        const isCompleted = !!note.completed;
        const classes = [
          'note-list-item',
          selectedId === note.id ? 'selected' : '',
          isInWork ? 'note-list-item-in-work' : '',
          isCompleted ? 'note-list-item-completed' : '',
        ].filter(Boolean).join(' ');
        return (
          <li key={note.id} className={classes} onClick={() => onSelect(note.id)}>
            <span className="note-list-item-title">{note.title || '(Без названия)'}</span>
            <span className="note-list-item-badges">
              {isInWork && <span className="note-list-badge note-list-badge-work">В работе</span>}
              {isCompleted && <span className="note-list-badge note-list-badge-done">Выполнена</span>}
            </span>
            <span className="note-list-item-preview">
              {note.body ? note.body.slice(0, 60) + (note.body.length > 60 ? '…' : '') : ''}
            </span>
            <span className="note-list-item-date">
              {note.createdAt ? new Date(note.createdAt).toLocaleDateString() : ''}
            </span>
          </li>
        );
      })}
    </ul>
  );
}
