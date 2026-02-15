#!/usr/bin/env python3
"""Скачивает стандарты с v8std.ru и сохраняет в content/ как markdown.

Полный список из раздела ИТС (its.1c.ru/db/v8std#browse:13:-1):
все подразделы — Создание и изменение объектов метаданных (1), Реализация
обработки данных (26), Соглашения при написании кода (31: 32, 33, 34),
Клиент-серверное взаимодействие (35), Общие вопросы безопасности (36),
Реализация обмена данными (38), Разработка и использование библиотек (39),
Требования по локализации (40), Проектирование интерфейсов 8.3 (7), 8.2 (15),
Разработка пользовательских интерфейсов (11), обычное приложение (23).
"""
import re
import urllib.request
from html import unescape
from pathlib import Path

CONTENT_DIR = Path(__file__).resolve().parent.parent / "content"
BASE = "https://v8std.ru/std"

# Соглашения при написании кода (31): Оформление модулей (32), Конструкции (33), Прикладные объекты (34)
STD_IDS_SECTION_31 = [
    456, 455, 647, 640, 454, 686,  # Оформление модулей
    441, 444, 440, 439, 442, 445, 492, 639, 494, 498, 499, 790, 547,  # Конструкции встроенного языка
    782, 781, 693, 407, 409, 411, 544, 486, 451, 449, 450, 448, 447, 452,  # Прикладные объекты и коллекции
]
# Реализация обработки данных (26)
STD_IDS_SECTION_26 = [
    787, 758, 726, 535, 412, 434, 435, 438, 436, 437,
    729, 652, 654, 655, 656, 657, 658, 708, 733, 777, 791, 792,
    497, 496, 648, 490, 460, 783, 661, 664, 663, 662, 659,
]
# Создание и изменение объектов метаданных (1)
STD_IDS_SECTION_1 = [
    467, 550, 643, 470, 413, 543, 677, 704, 469, 557, 785, 556, 680, 709, 723,
    480, 706, 731, 759, 798,
]
# Клиент-серверное взаимодействие (35), Общие вопросы безопасности (36), Обмен (38), Библиотеки (39), Локализация (40)
STD_IDS_SECTION_35 = [748, 725, 542, 629, 487, 443, 459, 724]
STD_IDS_SECTION_36 = [794, 775, 774, 770, 669, 740, 679, 678]
STD_IDS_SECTION_38 = [771, 701, 637]
STD_IDS_SECTION_39 = [690, 668, 644, 739, 554, 705, 553, 552, 551]
STD_IDS_SECTION_40 = [784, 778, 766, 767, 765, 764, 763, 762, 761, 769, 458]
# Интерфейсы: 8.3 (7), пользовательские (11), 8.2 (15), обычное приложение (23)
STD_IDS_SECTION_7 = [687, 722, 753, 727]
STD_IDS_SECTION_11 = [789, 548, 755, 642, 430, 468]
STD_IDS_SECTION_15 = [667, 665, 423, 596, 586, 585, 615, 578, 401, 576, 600, 566]
STD_IDS_SECTION_23 = [524, 502, 501, 500]

ALL_STD_IDS = (
    [453, 641, 788]  # описание процедур/функций, структуры в параметрах, новые разделы
    + STD_IDS_SECTION_31 + STD_IDS_SECTION_26 + STD_IDS_SECTION_1
    + STD_IDS_SECTION_35 + STD_IDS_SECTION_36 + STD_IDS_SECTION_38 + STD_IDS_SECTION_39 + STD_IDS_SECTION_40
    + STD_IDS_SECTION_7 + STD_IDS_SECTION_11 + STD_IDS_SECTION_15 + STD_IDS_SECTION_23
)
ALL_STD_IDS = sorted(set(ALL_STD_IDS))

def extract_markdown(html: str) -> str:
    # Убираем скрипты и стили
    html = re.sub(r'<script[^>]*>.*?</script>', '', html, flags=re.DOTALL|re.I)
    html = re.sub(r'<style[^>]*>.*?</style>', '', html, flags=re.DOTALL|re.I)
    # Ищем основной контент (на v8std.ru часто в article или div с классом)
    for pattern in [r'<article[^>]*>(.*?)</article>', r'<main[^>]*>(.*?)</main>',
                    r'<div class="[^"]*content[^"]*"[^>]*>(.*?)</div>', r'<body[^>]*>(.*?)</body>']:
        m = re.search(pattern, html, re.DOTALL|re.I)
        if m:
            html = m.group(1)
            break
    # Сохраняем блоки pre/code как есть
    def code_repl(m):
        inner = m.group(1)
        inner = re.sub(r'<[^>]+>', '', inner)
        inner = unescape(inner).strip()
        return '\n```bsl\n' + inner + '\n```\n'
    html = re.sub(r'<pre[^>]*><code[^>]*>(.*?)</code></pre>', code_repl, html, flags=re.DOTALL|re.I)
    html = re.sub(r'<pre[^>]*>(.*?)</pre>', code_repl, html, flags=re.DOTALL|re.I)
    # Заголовки
    html = re.sub(r'<h1[^>]*>(.*?)</h1>', r'\n# \1\n', html, flags=re.DOTALL|re.I)
    for i in range(6, 0, -1):
        html = re.sub(r'<h' + str(i) + r'[^>]*>(.*?)</h' + str(i) + r'>', r'\n' + '#' * (i+1) + r' \1\n', html, flags=re.DOTALL|re.I)
    html = re.sub(r'<li[^>]*>(.*?)</li>', r'- \1\n', html, flags=re.DOTALL|re.I)
    html = re.sub(r'<p[^>]*>(.*?)</p>', r'\1\n', html, flags=re.DOTALL|re.I)
    html = re.sub(r'<br\s*/?>', '\n', html, flags=re.I)
    text = re.sub(r'<[^>]+>', '', html)
    text = unescape(text)
    text = re.sub(r'&nbsp;', ' ', text)
    text = re.sub(r'\n{3,}', '\n\n', text)
    text = re.sub(r'[ \t]+', ' ', text)
    text = re.sub(r' *\n', '\n', text)
    return text.strip()

def fetch_std(num: int) -> str:
    url = f"{BASE}/{num}/"
    with urllib.request.urlopen(url, timeout=15) as r:
        return r.read().decode('utf-8', errors='replace')

def main():
    import sys
    ids = ALL_STD_IDS
    if len(sys.argv) > 1:
        ids = [int(x) for x in sys.argv[1:]]
    CONTENT_DIR.mkdir(parents=True, exist_ok=True)
    for num in ids:
        try:
            print(f"Fetching std/{num}...", end=" ", flush=True)
            html = fetch_std(num)
            md = extract_markdown(html)
            if len(md) < 200:
                print("skip (short)")
                continue
            out = CONTENT_DIR / f"std-{num}.md"
            out.write_text(md, encoding='utf-8')
            print(f"-> {out.name} ({len(md)} chars)")
        except urllib.error.HTTPError as e:
            print(f"HTTP {e.code}")
        except Exception as e:
            print(f"error: {e}")

if __name__ == "__main__":
    main()
