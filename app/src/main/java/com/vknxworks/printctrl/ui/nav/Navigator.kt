package com.vknxworks.printctrl.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController

/** One place that knows where things open. */
class Navigator(val nav: NavHostController) {

    /** True when the screen on top is fully shown (not still animating in or already on its way out). */
    private fun settled(): Boolean =
        nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    /** Opens a screen from a tap, ignoring taps on a screen that's already leaving or not shown yet. */
    fun navigate(route: Any) {
        if (!settled()) return
        nav.navigate(route) { launchSingleTop = true }
    }

    /**
     * Back from the screen the user is looking at. Repeat taps while a screen change is still
     * animating are ignored, and the first screen is never popped.
     */
    fun back() {
        val current = nav.currentBackStackEntry ?: return
        if (nav.previousBackStackEntry == null) return
        if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        nav.popBackStack()
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
