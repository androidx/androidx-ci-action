package dev.androidx.ci.testRunner.vo

import dev.androidx.ci.gcloud.GcsPath

/**
 * Contains information about an APK in our Cloud bucket.
 */
interface CloudApk {
    val gcsPath: GcsPath
}