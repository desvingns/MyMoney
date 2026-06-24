# Integer amounts + currency symbol after the number
Epic: dashboard-aurora-polish
Order: 01 of 03
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
WHAT: На дашборде показывать сумму остатка и суммы в пилюлях доход/расход только целыми числами (дробную часть ОТСЕКАТЬ, не округлять), а символ валюты — ПОСЛЕ числа. Касается главной Aurora-карточки и per-currency карточек режима «раздельно».
LAYERS: presentation
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (G4: `formatMoney` L411–420 — добавить `symbolPosition = MoneyFormatter.SymbolPosition.AFTER`; `formatBalanceAmount` L396–408 и `formatMoney` — отсекать дробь перед форматированием); feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt (G6: `formatCardAmount` L99–109 — отсекать дробь; AFTER там уже есть); новый локальный помощник усечения в :feature:dashboard (напр. `private fun truncate(a: BigDecimal) = a.setScale(0, RoundingMode.DOWN)`); G5: общий core/common/.../money/MoneyFormatter.kt НЕ менять; тесты app/src/androidTest/.../AuroraBalanceCardUiTest.kt + DashboardContentUiTest.kt (+ тесты CurrencyBalanceCardList, если есть) — обновить ожидаемые строки.
TEST_TYPES: unit compose-ui
CONSTRAINTS: «Отсекать» = усечение к нулю `RoundingMode.DOWN`: 1234.56 → «1234», −1234.56 → «−1234» (это НЕ округление — 1234.56 НЕ должно стать 1235; именно поэтому нельзя просто decimalDigits=0, т.к. MoneyFormatter округляет HALF_UP — G5/H2). Формат целого: `decimalDigits = 0`, групповые разделители сохраняются (напр. «1 234 567 ₸»), разделитель — по locale (`DecimalFormatSymbols`). Валюта строго ПОСЛЕ числа с пробелом во ВСЕХ пилюлях (главная карточка чинится здесь; per-currency уже AFTER). НЕ трогать общий MoneyFormatter, ChartConfig, поведение/настройки графика, форматирование сумм вне дашборда (плитки категорий, центр кольца и т.п. — вне scope). Обновить устаревшие тесты на новые строки (целые + символ после).
=== END SPEC ===

## Gap / context
Пилюли дохода/расхода главной карточки берут `formatMoney` без `symbolPosition` (G4) → срабатывает
дефолт `BEFORE` (G5) → валюта печатается перед числом. И остаток, и пилюли показывают дробные знаки
(`decimalDigits = currency.decimalDigits`). Нужно: валюту после числа и только целые — с отсечением,
а не округлением (decimalDigits=0 в MoneyFormatter округлил бы по HALF_UP).

## Implementation links
- commit: e49af3dd561a7972bbedf2e7c93ed3ec30af2ec2, b636ebee32c708daf449459d366552f3bbd3576f
- files:  feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardList.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/AuroraBalanceCardUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/CurrencyBalanceCardListUiTest.kt
