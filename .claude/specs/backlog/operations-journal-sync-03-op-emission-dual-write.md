# Эмиссия операций при мутациях (dual-write)
Epic: operations-journal-sync
Order: 03 of 07
Status: backlog
Depends-on: 01, 02
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Каждая пользовательская мутация транзакции/категории/счёта дополнительно пишет op в журнал (dual-write) — Room остаётся источником истины (D4). В `upsert`/`delete`/`archive` соответствующих `RepositoryImpl`: для новой записи генерируется `uuid`, проставляются `deviceId` (из `DeviceIdProvider`) и `updatedAt` (из инжектируемых часов), и В ТОЙ ЖЕ Room-транзакции в `op_journal` пишется `Operation` (`Upsert` со снимком записи / `Delete`). Плюс реализация `DeviceIdProvider` на DataStore (стабильный install-UUID).
LAYERS: data
CHANGED_HINT:
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/TransactionRepositoryImpl.kt — `upsert(t)`: если нет `uuid` — сгенерировать; проставить `deviceId`+`updatedAt`; в `@Transaction` вместе с upsert строки вставить `OperationEntity(Upsert, payload=снимок)` через `OperationDao` (02). `softDelete(id,now)` (G17): дополнительно вставить `OperationEntity(Delete)`. (G17, G18; путь Impl — assumption)
  - core/database/.../repository/CategoryRepositoryImpl.kt — `upsert`/`archive(id)` (G17) → эмиссия `Upsert`/`Delete`; проставить `uuid`/`deviceId`/`updatedAt` (новая колонка из 02). (G17; путь — assumption)
  - core/database/.../repository/AccountRepositoryImpl.kt — `upsert`/`archive(id)` (G17) → эмиссия `Upsert`/`Delete`; проставить `uuid`/`deviceId`/`updatedAt`. (G17; путь — assumption)
  - core/database/.../journal/OperationPayloadCodec.kt — новый: kotlinx.serialization JSON-кодек снимок-сущности ⇄ `payload` (общий с применением в 04); деньги в снимке — как в Room (Double) либо строкой BigDecimal — зафиксировать единый формат (G21). FK (categoryId/accountId транзакции, и т.п.) сериализуются ПО `uuid`, НЕ по локальному Long id (он у каждого устройства свой) — applier (04) резолвит uuid→localId. (assumption)
  - core/datastore/src/main/.../DeviceIdProviderImpl.kt — новый: `@Singleton`, реализует `DeviceIdProvider` (01); читает/при отсутствии генерирует и персистит install-UUID в DataStore Preferences (D9); Hilt-binding в datastore-модуле. (assumption, D9)
TEST_TYPES: dao, unit
CONSTRAINTS:
  - Эмиссия op и мутация строки — в ОДНОЙ Room-`@Transaction` (атомарно: либо обе, либо ни одной); тест на реальном in-memory Room (G26 — фейки только на границе репозитория, mock-фреймворк не вводить).
  - `updatedAt` — из ИНЖЕКТИРУЕМЫХ часов (есть `ClockModule`-провайдер из currency-эпика), НЕ `Instant.now()` инлайн — иначе тесты недетерминированы; `deviceId` — из `DeviceIdProvider`.
  - LOOP-GUARD (D12): применение удалённых ops (SPEC 04) пишет в таблицы НАПРЯМУЮ через DAO, минуя эти методы → не ре-эмитит. Эти методы эмитят ТОЛЬКО пользовательские мутации. Не вызывать публичные `upsert`/`archive` из applier.
  - Новые записи ВСЕГДА получают `uuid` (координируется с UNIQUE-индексом из 02) — нельзя оставлять `''`; вставка с пустым `uuid` — fail-fast (`require`), чтобы инвариант индекса ловился сразу, а не на 2-й вставке.
  - `OperationPayloadCodec` — единый формат payload, общий с применением (04); рассинхрон формата ломает merge.
  - Изменение сигнатур/поведения репозиториев рябит на модуль-локальные фейки `TransactionRepository`/`CategoryRepository`/`AccountRepository` во ВСЕХ потребляющих `:feature:*` (G26) — обновить.
=== END SPEC ===

## Acceptance
```gherkin
Feature: Журналирование пользовательских мутаций
  Покрывает dual-write в репозиториях транзакций, категорий, счетов.

  Scenario: Создание транзакции пишет операцию Upsert
    Given пустой журнал
    When пользователь создаёт транзакцию
    Then в журнале появляется операция Upsert с её uuid, deviceId и updatedAt
    And транзакция и операция записаны атомарно

  Scenario: Удаление категории пишет операцию Delete
    Given существующая категория с uuid
    When пользователь удаляет (архивирует) категорию
    Then в журнале появляется операция Delete с тем же uuid

  Scenario: Правка переиспользует существующий uuid
    Given транзакция с уже присвоенным uuid
    When пользователь редактирует её
    Then операция Upsert несёт ТОТ ЖЕ uuid и более поздний updatedAt

  Scenario: deviceId берётся из провайдера и стабилен
    Given провайдер install-UUID
    When создаются две записи подряд
    Then обе операции несут один и тот же deviceId
```

## Gap / context
Мутации сейчас пишут только в таблицы состояния (G18) — журнал из 02 остаётся пустым, синхронизировать нечего. Этот SPEC включает запись ops на каждой пользовательской мутации (объём v1: tx/cat/acc) и даёт стабильный `deviceId`.

## Implementation links
- commit: (pending)
- files:  (pending)
