package com.movtery.zalithlauncher.utils

import android.view.View

/**
 * Debounce click listener to prevent multiple rapid clicks
 */
class DebounceClickListener(
    private val debounceDelay: Long = 500L,
    private val onClick: (View) -> Unit
) : View.OnClickListener {
    
    private var lastClickTime = 0L
    
    override fun onClick(v: View) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= debounceDelay) {
            lastClickTime = currentTime
            onClick(v)
        }
    }
}

/**
 * Extension function to set a debounced click listener on any View
 */
fun View.setDebouncedClickListener(debounceDelay: Long = 500L, onClick: (View) -> Unit) {
    setOnClickListener(DebounceClickListener(debounceDelay, onClick))
}
