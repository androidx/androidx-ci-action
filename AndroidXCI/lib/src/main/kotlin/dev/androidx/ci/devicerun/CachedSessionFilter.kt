package dev.androidx.ci.devicerun

import dev.androidx.ci.generated.ddp.Session

/**
 * Filter interface that is used to decide whether a DDP Session result can be re-used.
 *
 * Return `true` if the cached Session can be re-used, false otherwise
 */
typealias CachedSessionFilter = suspend (Session) -> Boolean