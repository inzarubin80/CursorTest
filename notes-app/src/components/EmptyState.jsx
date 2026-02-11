import './EmptyState.css';

/**
 * @param {{ onCreateClick: () => void }}
 */
export default function EmptyState({ onCreateClick }) {
  return (
    <div className="empty-state">
      <p className="empty-state-message">Заметок пока нет.</p>
      <p className="empty-state-hint">Создайте первую заметку.</p>
      <button type="button" className="empty-state-button" onClick={onCreateClick}>
        Новая заметка
      </button>
    </div>
  );
}
