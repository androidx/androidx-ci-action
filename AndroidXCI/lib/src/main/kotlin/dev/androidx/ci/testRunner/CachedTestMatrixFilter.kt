package dev.androidx.ci.testRunner

import dev.androidx.ci.generated.ftl.TestMatrix

/**
 * Filter interface that is used to decide whether a TestMatrix result can be re-used.
 *
 * Return `true` if the cached TestMatrix can be re-used, false otherwise
 */
typealias CachedTestMatrixFilter = (TestMatrix) -> Boolean