/**
 * Демо-файл для показа Inline Edit (Ctrl+K / Cmd+K).
 * Выделяйте фрагменты и пробуйте сценарии из INLINE_EDIT_DEMO.md
 */

// --- Сценарий 1: добавить проверки ---
function formatUserName(user) {
  if (!user) return '';
  return [user.firstName, user.lastName].filter(Boolean).join(' ').trim();
}

// --- Сценарий 2: добавить типы / JSDoc ---
/**
 * Считает итоговую сумму по массиву позиций (price * qty).
 * @param {Array<{price: number, qty: number}>} items - массив позиций с ценой и количеством
 * @returns {number} сумма
 */
function calcTotal(items) {
  let sum = 0;
  for (let i = 0; i < items.length; i++) {
    sum += items[i].price * items[i].qty;
  }
  return sum;
}

// --- Сценарий 3: упростить / переписать современнее ---
function findActive(users) {
  const result = [];
  for (let i = 0; i < users.length; i++) {
    if (users[i].active === true) {
      result.push(users[i]);
    }
  }
  return result;
}

// --- Сценарий 4: разбить на функции / вынести константы ---
function getGreeting(lang, isFormal) {
  if (lang === 'ru') {
    if (isFormal) {
      return 'Здравствуйте';
    } else {
      return 'Привет';
    }
  } else if (lang === 'en') {
    if (isFormal) {
      return 'Hello';
    } else {
      return 'Hi';
    }
  }
  return 'Hi';
}

// --- Сценарий 5: добавить обработку ошибок ---
function parseJson(str) {
  return JSON.parse(str);
}
