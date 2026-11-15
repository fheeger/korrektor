package korrektor.viewer

/** The screen position of the page's edge: negative when scrolled, positive when the page is centred. */
internal fun origin(
    content: Int,
    viewport: Int,
    offset: Double,
): Double = if (content < viewport) (viewport - content) / 2.0 else -offset

internal fun clamp(
    offset: Double,
    content: Int,
    viewport: Int,
): Double = if (content <= viewport) 0.0 else offset.coerceIn(0.0, (content - viewport).toDouble())
