const STORAGE_KEY = 'notes-app-data';
const CURRENT_WORK_KEY = 'notes-app-current-work';

/**
 * @returns {import('../types/note').Note[]}
 */
export function getNotes() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw == null) return [];
    const parsed = JSON.parse(raw);
    const list = Array.isArray(parsed) ? parsed : [];
    return list.map((note) => ({
      ...note,
      completed: note.completed ?? false,
      workSessions: note.workSessions ?? [],
    }));
  } catch {
    return [];
  }
}

/**
 * @returns {{ noteId: string | null, startedAt: number | null }}
 */
export function getCurrentWork() {
  try {
    const raw = localStorage.getItem(CURRENT_WORK_KEY);
    if (raw == null) return { noteId: null, startedAt: null };
    const parsed = JSON.parse(raw);
    return {
      noteId: parsed?.noteId ?? null,
      startedAt: parsed?.startedAt ?? null,
    };
  } catch {
    return { noteId: null, startedAt: null };
  }
}

/**
 * @param {{ noteId: string | null, startedAt: number | null }} payload
 */
export function setCurrentWork(payload) {
  localStorage.setItem(CURRENT_WORK_KEY, JSON.stringify(payload));
}

/**
 * @param {import('../types/note').Note[]} notes
 */
export function saveNotes(notes) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(notes));
}

/**
 * @param {{ title?: string, body?: string }} [props]
 * @returns {import('../types/note').Note}
 */
export function createNote(props = {}) {
  const now = Date.now();
  return {
    id: String(now),
    title: props.title ?? '',
    body: props.body ?? '',
    createdAt: now,
    updatedAt: now,
    completed: false,
    workSessions: [],
  };
}

/**
 * Builds JSON payload and triggers download of a .json file.
 * Uses current data from localStorage (getNotes + getCurrentWork).
 */
export function downloadExportJson() {
  const notes = getNotes();
  const currentWork = getCurrentWork();
  const payload = { notes, currentWork };
  const json = JSON.stringify(payload, null, 2);
  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const date = new Date();
  const dateStr = date.toISOString().slice(0, 10);
  const filename = `notes-export-${dateStr}.json`;
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Parses JSON string from import file.
 * Accepts: { notes: [...] } or raw array [...].
 * Normalizes each note (completed, workSessions).
 * Does NOT write to localStorage.
 * @param {string} raw
 * @returns {{ notes: import('../types/note').Note[], currentWork: { noteId: string | null, startedAt: number | null } }}
 * @throws {Error} on invalid JSON or missing notes
 */
export function parseImportJson(raw) {
  const parsed = JSON.parse(raw);
  let notes = Array.isArray(parsed) ? parsed : parsed?.notes;
  if (!Array.isArray(notes)) throw new Error('Неверный формат: нужен массив заметок или объект с полем notes');
  notes = notes.map((note) => ({
    ...note,
    completed: note.completed ?? false,
    workSessions: note.workSessions ?? [],
  }));
  let currentWork = null;
  if (!Array.isArray(parsed) && parsed?.currentWork != null && typeof parsed.currentWork === 'object') {
    const cw = parsed.currentWork;
    const noteId = typeof cw.noteId === 'string' ? cw.noteId : null;
    const startedAt = typeof cw.startedAt === 'number' || cw.startedAt === null ? cw.startedAt : null;
    currentWork = { noteId, startedAt };
  }
  if (currentWork === null) currentWork = { noteId: null, startedAt: null };
  return { notes, currentWork };
}
