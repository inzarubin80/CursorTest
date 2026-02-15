import { useState, useEffect } from 'react';
import './NoteView.css';

function formatElapsed(ms) {
  const totalSeconds = Math.floor(ms / 1000);
  const h = Math.floor(totalSeconds / 3600);
  const m = Math.floor((totalSeconds % 3600) / 60);
  const s = totalSeconds % 60;
  if (h > 0) {
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  }
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

function totalWorkMinutes(workSessions) {
  if (!workSessions || workSessions.length === 0) return 0;
  const totalMs = workSessions.reduce(
    (sum, s) => sum + (s.endedAt - s.startedAt),
    0
  );
  return Math.round(totalMs / 60000);
}

function timestampToDatetimeLocal(ts) {
  const d = new Date(ts);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function datetimeLocalToTimestamp(value) {
  if (!value) return null;
  const t = new Date(value).getTime();
  return Number.isFinite(t) ? t : null;
}

/**
 * @param {{
 *   note: import('../types/note').Note,
 *   currentWork: { noteId: string | null, startedAt: number | null },
 *   onEdit: () => void,
 *   onDelete: () => void,
 *   onTakeToWork: (noteId: string) => void,
 *   onStartWork: () => void,
 *   onEndWork: (whatDone?: string) => void,
 *   onToggleCompleted: (noteId: string) => void,
 *   onUpdateWorkSession: (noteId: string, sessionIndex: number, payload: { startedAt: number, endedAt: number, whatDone?: string }) => void,
 * }}
 */
export default function NoteView({
  note,
  currentWork,
  onEdit,
  onDelete,
  onTakeToWork,
  onStartWork,
  onEndWork,
  onToggleCompleted,
  onUpdateWorkSession,
}) {
  const isCurrentWork = currentWork?.noteId === note.id;
  const timerStartedAt = isCurrentWork ? currentWork.startedAt : null;

  const [elapsedMs, setElapsedMs] = useState(0);
  const [showWhatDoneForm, setShowWhatDoneForm] = useState(false);
  const [whatDoneDraft, setWhatDoneDraft] = useState('');
  const [editingSessionIndex, setEditingSessionIndex] = useState(null);
  const [sessionEditStart, setSessionEditStart] = useState('');
  const [sessionEditMode, setSessionEditMode] = useState('end'); // 'end' | 'duration'
  const [sessionEditEnd, setSessionEditEnd] = useState('');
  const [sessionEditDurationMin, setSessionEditDurationMin] = useState(0);
  const [sessionEditWhatDone, setSessionEditWhatDone] = useState('');

  useEffect(() => {
    if (timerStartedAt == null) return undefined;
    const id = setInterval(() => {
      setElapsedMs(Date.now() - timerStartedAt);
    }, 1000);
    return () => clearInterval(id);
  }, [timerStartedAt]);

  const handleEndWorkClick = () => {
    setShowWhatDoneForm(true);
  };

  const handleWhatDoneSubmit = (e) => {
    e.preventDefault();
    onEndWork(whatDoneDraft);
    setWhatDoneDraft('');
    setShowWhatDoneForm(false);
  };

  const handleWhatDoneCancel = () => {
    setWhatDoneDraft('');
    setShowWhatDoneForm(false);
  };

  const openSessionEdit = (session, index) => {
    setEditingSessionIndex(index);
    setSessionEditStart(timestampToDatetimeLocal(session.startedAt));
    setSessionEditMode('end');
    setSessionEditEnd(timestampToDatetimeLocal(session.endedAt));
    setSessionEditDurationMin(Math.round((session.endedAt - session.startedAt) / 60000));
    setSessionEditWhatDone(session.whatDone || '');
  };

  const closeSessionEdit = () => {
    setEditingSessionIndex(null);
  };

  const handleSessionEditSubmit = (e) => {
    e.preventDefault();
    if (editingSessionIndex == null || !onUpdateWorkSession) return;
    const startedAt = datetimeLocalToTimestamp(sessionEditStart);
    if (startedAt == null) return;
    let endedAt;
    if (sessionEditMode === 'duration') {
      const min = Number(sessionEditDurationMin);
      if (!Number.isFinite(min) || min < 0) return;
      endedAt = startedAt + min * 60000;
    } else {
      endedAt = datetimeLocalToTimestamp(sessionEditEnd);
      if (endedAt == null || endedAt <= startedAt) return;
    }
    onUpdateWorkSession(note.id, editingSessionIndex, {
      startedAt,
      endedAt,
      whatDone: sessionEditWhatDone?.trim() || undefined,
    });
    closeSessionEdit();
  };

  const totalMinutes = totalWorkMinutes(note.workSessions);

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

      <div className="note-view-meta">
        <label className="note-view-completed">
          <input
            type="checkbox"
            checked={!!note.completed}
            onChange={() => onToggleCompleted(note.id)}
          />
          <span>Выполнена</span>
        </label>
        {totalMinutes > 0 && (
          <span className="note-view-total-time">
            Итого: {totalMinutes} мин
          </span>
        )}
      </div>

      <p className="note-view-date">
        Создано: {note.createdAt ? new Date(note.createdAt).toLocaleString() : '—'}
        {note.updatedAt && note.updatedAt !== note.createdAt && (
          <> · Изменено: {new Date(note.updatedAt).toLocaleString()}</>
        )}
      </p>

      {!isCurrentWork && (
        <div className="note-view-work-block">
          <button
            type="button"
            className="note-view-take-work"
            onClick={() => onTakeToWork(note.id)}
          >
            Взять в работу
          </button>
        </div>
      )}

      {isCurrentWork && (
        <div className="note-view-work-block">
          <div className="note-view-timer">
            {formatElapsed(timerStartedAt != null ? elapsedMs : 0)}
          </div>
          <div className="note-view-work-actions">
            {timerStartedAt == null ? (
              <button type="button" className="note-view-start-work" onClick={onStartWork}>
                Начать работу
              </button>
            ) : (
              <button
                type="button"
                className="note-view-end-work"
                onClick={handleEndWorkClick}
              >
                Закончить работу
              </button>
            )}
          </div>
        </div>
      )}

      {showWhatDoneForm && (
        <form className="note-view-what-done" onSubmit={handleWhatDoneSubmit}>
          <label>
            <span>Что сделано?</span>
            <textarea
              value={whatDoneDraft}
              onChange={(e) => setWhatDoneDraft(e.target.value)}
              placeholder="Опишите выполненную работу..."
              rows={3}
              autoFocus
            />
          </label>
          <div className="note-view-what-done-actions">
            <button type="submit">Сохранить</button>
            <button type="button" onClick={handleWhatDoneCancel}>
              Отмена
            </button>
          </div>
        </form>
      )}

      <div className="note-view-body">{note.body || <em>Нет текста</em>}</div>

      {note.workSessions && note.workSessions.length > 0 && (
        <div className="note-view-sessions">
          <h3 className="note-view-sessions-title">Сессии работы</h3>
          <ul className="note-view-sessions-list">
            {note.workSessions.map((session, i) => {
              const isEditing = editingSessionIndex === i;
              const durationMs = session.endedAt - session.startedAt;
              return (
                <li key={i} className="note-view-session-item">
                  {!isEditing ? (
                    <>
                      <div className="note-view-session-time-row">
                        <span className="note-view-session-time">
                          {new Date(session.startedAt).toLocaleString()} —{' '}
                          {new Date(session.endedAt).toLocaleString()} (
                          {formatElapsed(durationMs)})
                        </span>
                        <button
                          type="button"
                          className="note-view-session-edit-btn"
                          onClick={() => openSessionEdit(session, i)}
                          title="Редактировать сессию"
                          aria-label="Редактировать сессию"
                        >
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                          </svg>
                        </button>
                      </div>
                      {session.whatDone && (
                        <p className="note-view-session-what">{session.whatDone}</p>
                      )}
                    </>
                  ) : (
                    <form className="note-view-session-edit" onSubmit={handleSessionEditSubmit}>
                      <label>
                        <span>Начало</span>
                        <input
                          type="datetime-local"
                          value={sessionEditStart}
                          onChange={(e) => setSessionEditStart(e.target.value)}
                          required
                        />
                      </label>
                      <div className="note-view-session-edit-mode">
                        <label>
                          <input
                            type="radio"
                            name="sessionEditMode"
                            checked={sessionEditMode === 'end'}
                            onChange={() => setSessionEditMode('end')}
                          />
                          <span>Конец</span>
                        </label>
                        <label>
                          <input
                            type="radio"
                            name="sessionEditMode"
                            checked={sessionEditMode === 'duration'}
                            onChange={() => setSessionEditMode('duration')}
                          />
                          <span>Длительность (мин)</span>
                        </label>
                      </div>
                      {sessionEditMode === 'end' ? (
                        <label>
                          <span>Конец</span>
                          <input
                            type="datetime-local"
                            value={sessionEditEnd}
                            onChange={(e) => setSessionEditEnd(e.target.value)}
                            required
                          />
                        </label>
                      ) : (
                        <label>
                          <span>Минут</span>
                          <input
                            type="number"
                            min={0}
                            step={1}
                            value={sessionEditDurationMin}
                            onChange={(e) => setSessionEditDurationMin(Number(e.target.value) || 0)}
                          />
                        </label>
                      )}
                      <label>
                        <span>Что сделано</span>
                        <textarea
                          value={sessionEditWhatDone}
                          onChange={(e) => setSessionEditWhatDone(e.target.value)}
                          rows={2}
                        />
                      </label>
                      <div className="note-view-session-edit-actions">
                        <button type="submit">Сохранить</button>
                        <button type="button" onClick={closeSessionEdit}>
                          Отмена
                        </button>
                      </div>
                    </form>
                  )}
                </li>
              );
            })}
          </ul>
        </div>
      )}
    </div>
  );
}
