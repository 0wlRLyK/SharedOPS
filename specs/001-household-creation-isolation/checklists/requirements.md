# Specification Quality Checklist: Створення домогосподарства та ізоляція даних

**Purpose**: Перевірка повноти й якості специфікації перед уточненням.
**Created**: 2026-11-19
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) in the behavioral specification
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders, with clearly isolated issue-mandated engineering constraints
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain for the M0 scope
- [x] Requirements are testable and unambiguous for currently available operations
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic
- [x] All in-scope acceptance scenarios are defined; future work is assigned to its owning issue
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All M0 functional requirements have clear acceptance scenarios
- [x] User scenarios cover primary flows
- [x] Success criteria define measurable target outcomes, not claims of completion
- [x] No implementation details leak into the behavioral specification

## Notes

- Engineering criteria are referenced individually to the exact ordered criteria in HO-10. Their full technical wording remains authoritative in that issue and MUST be transcribed without weakening into the plan and tasks. Agreed interface and migration details are kept separately below for that handoff; they do not constitute approval of the revised specification.
- HO-11 owns real invitation acceptance and create/join races; HO-50 owns the M2 currency-history policy and currency changes. Later operation owners must enforce and verify isolation on their actual paths. These are not acceptance prerequisites for the M0 scope of HO-10. Absent operations and unexecuted tests are not counted as passing.
- The current M0 specification and plan require their own recorded owner approvals; prior approvals do not automatically apply to revised content. Checklist markers describe specification quality, not implementation or verification.

## Передані вимоги до контракту

Цей додаток зберігає попередньо погоджені технічні деталі для автора плану і контракту. План і задачі повинні зберегти їх повністю; це не свідчення їх реалізації чи нового погодження.

- Успішне створення: `201` із `Location` і полями `id`, `name`, `icon`, `currency`, `timezone`; невибрана іконка повертається як `null`. Читання повертає ці ж поля.
- Повторне створення при активному членстві повертає `409 ACTIVE_HOUSEHOLD_EXISTS`. Некоректний формат ідентифікатора — `400 VALIDATION_ERROR` без повернення переданого значення; чужий, відсутній або недоступний через відсутнє/неактивне членство household — `404 NOT_FOUND`. Для 404 значення однакові: `title` = `Not Found`, `status` = `404`, `detail` = `Resource not found.`, `code` = `NOT_FOUND`; `instance` не додається. Відповідь не містить додаткових діагностичних полів; authentication і CSRF використовують наявний API-контракт.
- Перевіряти доступ у прикладних операціях та захищати інваріанти обмеженнями PostgreSQL.
- Міграція зупиняється за наявності старих рядків `households`; окреме перенесення потребує погодження власника. Не видаляти пов’язані дані і не вигадувати власників або валюту.

Окремі чотири критерії в розділі «Additional acceptance criteria transferred from HO-004» [HO-10](https://linear.app/home-0ps/issue/HO-10/implement-household-creation-and-data-isolation) є обов’язковим першоджерелом для проєктування та тестів; не вважати їх виконаними на підставі цього чекліста.

- **Погоджене відхилення редагування часового поясу в M0:** якщо існує операція зміни налаштувань і в запиті передано поле `timezone`, увесь запит відхиляється як помилка валідації `400`, а збережений стан залишається незмінним; якщо такої операції немає, жодна реалізована операція не приймає зміну поясу. План і тести MUST зберегти цю умовну вимогу та перевірити відповідний випадок; відсутність операції не доводить поведінку неіснуючого endpoint.
