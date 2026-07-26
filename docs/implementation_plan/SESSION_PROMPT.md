# Session prompt templates

Copy-paste these into a new Claude session to resume work on MyMoney.

---

## 1. Default prompt (continue active phase)

```
Продолжай реализацию Android-приложения MyMoney по чек-листу.

Стартовая последовательность:
1. Прочитай C:\Pet\MyMoney\docs\implementation_plan\PROGRESS.md — найди активную фазу.
2. Прочитай C:\Pet\MyMoney\docs\implementation_plan\README.md — §2 (Session protocol) и §4 (Conventions).
3. Открой phases\PHASE_NN_*.md (NN = активная фаза). Если её статус "in progress", прочитай "Notes for next session" внизу — там точка возобновления.
4. Прочитай TDD-секции из "TDD anchors" фазы. Только эти диапазоны строк, не весь TDD (он 2855 строк, контекст не выдержит).
5. Работай по Task checklist последовательно. При выполнении задачи меняй `- [ ]` → `- [x]` прямо в phase-файле.
6. После значимого блока кода прогоняй соответствующие Verification commands.

В конце сессии (обязательно):
- Заполни "Notes for next session" в phase-файле: сюрпризы, отложенное, точка возобновления если фаза не закрыта.
- Обнови PROGRESS.md: статус фазы, дата сессии, одна строка в Session log.
- Если фаза закрыта — переведи следующую в "active".
- Остановись. Не начинай следующую фазу в этой же сессии.

TDD: TDD/MyMoney/MyMoney_TDD.md (repo-relative). Глоссарий BR-/AS-/OQ- — README.md §5.
Отвечай мне по-русски. Код и артефакты — на английском.
```

---

## 2. Override prompt (jump to a specific phase)

Same as above, but prepend this line:

```
Работай по фазе PHASE_NN, даже если PROGRESS.md указывает другую активную.
```

Replace `NN` with the target phase number (`PHASE_08`, `PHASE_13`, etc.).

Use when:
- You want to re-do or audit a previously-closed phase.
- You discovered a bug introduced in phase X and want to revisit it.
- You're skipping a phase deliberately (e.g. PHASE_13 cloud sync blocked on OQ-1/2/3).

---

## 3. Bug-fix prompt (return to a phase to fix something)

```
Работай по фазе PHASE_NN, даже если PROGRESS.md указывает другую активную.

Контекст проблемы: <короткое описание бага, шаги воспроизведения, ожидаемое vs фактическое поведение>.

После исправления:
- Обнови Task checklist фазы PHASE_NN — добавь подзадачу с описанием бага и пометкой `[x]` после фикса.
- В Notes for next session фазы PHASE_NN запиши, что было сломано и как починили.
- НЕ меняй активную фазу в PROGRESS.md — ты только зашёл починить, основная работа продолжается на другой фазе.
- Запиши одну строку в Session log с пометкой "[hotfix PHASE_NN]".
```

---

## Когда какой шаблон

| Ситуация | Шаблон |
|---|---|
| Обычное продолжение работы | **#1 default** |
| Предыдущая сессия оставила фазу `in progress` | **#1 default** (Claude найдёт точку возобновления в Notes) |
| Хочу перепройти / доработать закрытую фазу | **#2 override** |
| Нашёл баг, нужно вернуться к старой фазе | **#3 bug-fix** |

---

## FAQ

**Q: А если я хочу начать с нуля, забыв всё что было?**
A: Не нужен новый шаблон — удали `PROGRESS.md` руками (или просто откати phase completion table в начальное состояние) и используй default. Но обычно лучше явно сказать в новом промпте, что и почему.

**Q: Какую модель Claude использовать для этих сессий?**
A: Opus 4.7 (1M context) для фаз с большим объёмом кода (PHASE_04 database, PHASE_08 donut, PHASE_10 transaction forms, PHASE_13 cloud sync). Sonnet 4.6 достаточен для остальных. Haiku — только для тривиальных доработок/багфиксов.

**Q: Что если фаза не помещается в одну сессию?**
A: Не пытайся ужать. В конце сессии: оставь статус `in progress`, в Notes for next session запиши resume point (какие задачи доделать), останови работу. Следующая сессия с default-шаблоном подберёт.

**Q: Я хочу запустить две параллельные фазы (например, PHASE_09 и PHASE_10 одновременно).**
A: Не делай. Архитектура чек-листа предполагает последовательное выполнение — фазы редактируют общие файлы (`MyMoneyNavHost.kt`, `libs.versions.toml`, `PROGRESS.md`), параллельность приведёт к мерж-конфликтам. Если очень хочется параллелить — нужны git worktrees + ручная синхронизация в конце.
