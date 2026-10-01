package io.vela.core.model

/** Where a background artwork and metadata run stands. */
sealed interface ScrapeProgress {
    data object Idle : ScrapeProgress
    data class Running(val done: Int, val total: Int, val currentTitle: String) : ScrapeProgress
    data class Finished(val scraped: Int, val failed: Int, val notFound: Int) : ScrapeProgress
    data class Stopped(val reason: String) : ScrapeProgress
}
