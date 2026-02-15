/**
 * @typedef {Object} WorkSession
 * @property {number} startedAt - timestamp
 * @property {number} endedAt - timestamp
 * @property {string} [whatDone] - optional description of what was done
 */

/**
 * @typedef {Object} Note
 * @property {string} id
 * @property {string} title
 * @property {string} body
 * @property {number} createdAt - timestamp
 * @property {number} [updatedAt] - timestamp, optional
 * @property {boolean} [completed] - whether the note is marked as done
 * @property {WorkSession[]} [workSessions] - work sessions (start, end, what done)
 */

export default {}
