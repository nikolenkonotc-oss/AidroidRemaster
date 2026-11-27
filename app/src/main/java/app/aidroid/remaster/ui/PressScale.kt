package app.aidroid.remaster.ui

/**
 * Returns a scale that removes a fixed number of pixels from a dimension.
 * Unlike a constant percentage, the visible press distance does not grow
 * when a message expands to several screens of text.
 */
internal fun fixedDimensionScale(
    dimensionPx: Float,
    shrinkPx: Float,
    progress: Float
): Float {
    if (!dimensionPx.isFinite() || dimensionPx <= 0f) return 1f
    if (!shrinkPx.isFinite() || shrinkPx <= 0f) return 1f
    val amount = shrinkPx.coerceAtMost(dimensionPx) * progress.coerceIn(0f, 1f)
    return (dimensionPx - amount) / dimensionPx
}
