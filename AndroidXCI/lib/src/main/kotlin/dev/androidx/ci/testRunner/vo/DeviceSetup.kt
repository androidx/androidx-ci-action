package dev.androidx.ci.testRunner.vo

import dev.androidx.ci.generated.ftl.Apk
import dev.androidx.ci.generated.ftl.EnvironmentVariable
import dev.androidx.ci.generated.ftl.FileReference
import dev.androidx.ci.generated.ftl.TestSetup

/**
 * Various test options that are applied to the device.
 */
data class DeviceSetup(
    /**
     * Additional APKs to install before running test. The order we install them has to be preserved.
     */
    val additionalApks: List<CloudApk>? = null,
    /**
     * List of directories on the device to upload to GCS at the end of the test; they must be
     * absolute paths under /sdcard, /storage or /data/local/tmp. Path names are restricted to
     * characters a-z A-Z 0-9 _ - . + and / Note: The paths /sdcard and /data will be made available
     * and treated as implicit path substitutions. E.g. if /sdcard on a particular device does not map
     * to external storage, the system will replace it with the external storage path prefix for that
     * device.
     */
    var directoriesToPull: MutableList<String> = mutableListOf(),
    /**
     * List of instrumentation arguments to be passed into the runner.
     */
    val instrumentationArguments: List<InstrumentationArgument>? = null
) {
    internal fun toTestSetup(): TestSetup {
        return TestSetup(
            additionalApks = additionalApks?.map { cloudApk ->
                Apk(
                    location = FileReference(
                        gcsPath = cloudApk.gcsPath.path
                    )
                )
            },
            directoriesToPull = directoriesToPull,
            environmentVariables = instrumentationArguments?.map {
                it.toEnvironmentVariable()
            }
        )
    }

    data class InstrumentationArgument(
        val key: String,
        val value: String
    ) {
        internal fun toEnvironmentVariable() = EnvironmentVariable(
            key = key,
            value = value
        )
    }
}
