package com.learnspark.core.di

import android.content.Context
import java.util.concurrent.atomic.AtomicReference

/**
 * Android Context 桥接：Migration 的 LegacyDataLocator 需在 commonMain 内
 * 拿到 Context，但不想引入 platform-specific API 到 commonMain。
 *
 * 由 [com.learnspark.MainActivity] 在 startKoin 之后注入。
 */
internal val androidContextLazy: AtomicReference<Context?> = AtomicReference(null)

/** 注入 Android Context（由 [com.learnspark.MainActivity] 调用） */
fun setAndroidContext(ctx: Context) {
    androidContextLazy.set(ctx)
}
