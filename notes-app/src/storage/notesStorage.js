const STORAGE_KEY = 'notes-app-data';

/**
 * @returns {import('../types/note').Note[]}
 */
export function getNotes() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw == null) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
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
  };
}
