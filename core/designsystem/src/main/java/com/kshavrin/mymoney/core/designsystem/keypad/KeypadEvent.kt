package com.kshavrin.mymoney.core.designsystem.keypad

sealed interface KeypadEvent {
    data class Digit(val d: Int) : KeypadEvent
    data class Op(val op: Operator) : KeypadEvent
    object Dot : KeypadEvent
    object Backspace : KeypadEvent
    object Equals : KeypadEvent
}
