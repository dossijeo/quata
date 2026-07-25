package com.quata.ios.shared

/**
 * Gives the iOS-only umbrella a Kotlin compilation unit.
 *
 * The module is intentionally an export boundary rather than an application
 * layer: its framework configuration re-exports the feature APIs consumed by
 * Swift, while this internal marker prevents Kotlin/Native from treating the
 * framework link task as `NO-SOURCE`.
 */
internal object QuataSharedFrameworkBoundary
