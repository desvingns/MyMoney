package com.kshavrin.mymoney.core.designsystem.picker

/**
 * Category icon keys selectable in the [IconPickerGrid], split by [com.kshavrin.mymoney.core.domain] kind.
 *
 * These mirror the keys resolved by [com.kshavrin.mymoney.core.designsystem.icon.categoryIcon]; every key
 * here MUST map to a real vector there. Adding a key without a matching registry entry falls back to the
 * generic "other" glyph.
 */
val CATEGORY_EXPENSE_ICON_KEYS: List<String> =
    listOf(
        "ic_cat_clothing",
        "ic_cat_bills",
        "ic_cat_food",
        "ic_cat_entertainment",
        "ic_cat_taxi",
        "ic_cat_housing",
        "ic_cat_health",
        "ic_cat_pets",
        "ic_cat_sport",
        "ic_cat_gifts",
        "ic_cat_phone",
        "ic_cat_transport",
        "ic_cat_hygiene",
        "ic_cat_cafe",
        "ic_cat_car",
        "ic_cat_groceries",
        "ic_cat_restaurant",
        "ic_cat_fastfood",
        "ic_cat_coffee",
        "ic_cat_bar",
        "ic_cat_alcohol",
        "ic_cat_bus",
        "ic_cat_tram",
        "ic_cat_flight",
        "ic_cat_bike",
        "ic_cat_fuel",
        "ic_cat_parking",
        "ic_cat_shoes",
        "ic_cat_electronics",
        "ic_cat_books",
        "ic_cat_rent",
        "ic_cat_utilities",
        "ic_cat_water",
        "ic_cat_furniture",
        "ic_cat_repair",
        "ic_cat_pharmacy",
        "ic_cat_doctor",
        "ic_cat_dentist",
        "ic_cat_gym",
        "ic_cat_beauty",
        "ic_cat_education",
        "ic_cat_kids",
        "ic_cat_baby",
        "ic_cat_travel",
        "ic_cat_hotel",
        "ic_cat_subscription",
        "ic_cat_streaming",
        "ic_cat_internet",
        "ic_cat_charity",
    )

val CATEGORY_INCOME_ICON_KEYS: List<String> =
    listOf(
        "ic_cat_salary",
        "ic_cat_other",
        "ic_cat_freelance",
        "ic_cat_bonus",
        "ic_cat_dividends",
        "ic_cat_interest",
        "ic_cat_rent_income",
        "ic_cat_business_income",
        "ic_cat_sale",
        "ic_cat_refund",
        "ic_cat_gift_received",
        "ic_cat_cashback",
        "ic_cat_pension",
        "ic_cat_scholarship",
        "ic_cat_investment_return",
        "ic_cat_royalties",
        "ic_cat_tips",
        "ic_cat_deposit_income",
    )

/** Default category color swatches offered by [ColorPickerGrid]. */
val CATEGORY_COLOR_PALETTE: List<String> =
    listOf(
        "#9C5BB8",
        "#C9A227",
        "#E07AAE",
        "#F08A3E",
        "#E0A52C",
        "#4A8FCB",
        "#D85A5A",
        "#3DA98A",
        "#7AC29A",
        "#D9A4A4",
        "#9CBBA8",
        "#E07A7A",
        "#3A4F8C",
        "#7A9685",
        "#4A5870",
    )
