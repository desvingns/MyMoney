package com.kshavrin.mymoney.core.common.result

sealed class AppResult<out T> {
    data class Success<out T>(
        val data: T,
    ) : AppResult<T>()

    data class Error(
        val cause: Throwable,
    ) : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onError(action: (Throwable) -> Unit): AppResult<T> {
    if (this is AppResult.Error) action(cause)
    return this
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> this
    }

inline fun <T> AppResult<T>.getOrElse(default: (Throwable) -> T): T =
    when (this) {
        is AppResult.Success -> data
        is AppResult.Error -> default(cause)
    }

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data
