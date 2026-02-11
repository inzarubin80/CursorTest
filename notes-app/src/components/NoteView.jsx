import './NoteView.css';

/**
 * @param {{ note: import('../types/note').Note, onEdit: () => void, onDelete: () => void }}
 */
export default function NoteView({ note, onEdit, onDelete }) {
  return (
    <div className="note-view">
      <div className="note-view-header">
        <h1 className="note-view-title">{note.title || '(Без названия)'}</h1>
        <div className="note-view-actions">
          <button type="button" onClick={onEdit}>
            Редактировать
          </button>
          <button type="button" className="note-view-delete" onClick={onDelete}>
            Удалить
          </button>
        </div>
      </div>
      <p className="note-view-date">
        Создано: {note.createdAt ? new Date(note.createdAt).toLocaleString() : '—'}
        {note.updatedAt && note.updatedAt !== note.createdAt && (
          <> · Изменено: {new Date(note.updatedAt).toLocaleString()}</>
        )}
      </p>
      <div className="note-view-body">{note.body || <em>Нет текста</em>}</div>
    </div>
  );
}
