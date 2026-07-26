# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-07-26

### Added

- v1.0 baseline: an offline-first Android money tracker with accounts, categories, transactions, dashboard periods and charts, dictionaries, settings, backup and import/export, recurring operations, budgets, app lock, and English/Russian localization.
- Journal sync: append-only operation journaling for accounts, categories, and transactions, with deterministic merge and tombstones, identity migration, shared-folder transport, orchestration, and dashboard refresh/status surfaces.
- Dashboard summary: a period-aware bottom-sheet chronology of income, expenses, and transfers, opened from the Aurora card, balance, or category tiles with the applicable account/currency/category scope.
- Aurora: refined the dashboard hero section with compact wide layouts, integer amount formatting, trailing currency placement, sign-aware neon styling, and accessible balance contrast.

This initial entry is a summary of shipped epics recorded in the implementation plan and completed SPECs; it does not rewrite their historical logs.
