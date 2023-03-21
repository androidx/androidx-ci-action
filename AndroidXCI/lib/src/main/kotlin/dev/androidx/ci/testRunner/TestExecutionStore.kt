package dev.androidx.ci.testRunner

import dev.androidx.ci.datastore.DatastoreApi
import dev.androidx.ci.firebase.FirebaseTestLabApi
import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.gcloud.GoogleCloudApi

internal class TestExecutionStore(
    private val toolsResultApi: ToolsResultApi,
    private val googleCloudApi: GoogleCloudApi,
    firebaseProjectId: String,
    datastoreApi: DatastoreApi,
    firebaseTestLabApi: FirebaseTestLabApi,
    gcsResultPath: String
) {
    private val testMatrixStore = TestMatrixStore(
        firebaseProjectId = firebaseProjectId,
        datastoreApi = datastoreApi,
        firebaseTestLabApi = firebaseTestLabApi,
        toolsResultApi = toolsResultApi,
        resultsGcsPrefix = googleCloudApi.getGcsPath("aosp-ftl/$gcsResultPath")
    )
    private val testLabController = FirebaseTestLabController(
        firebaseTestLabApi = firebaseTestLabApi,
        firebaseProjectId = firebaseProjectId,
        testMatrixStore = testMatrixStore
    )

    private suspend fun getTestExecutionStepsLogcats(
        projectId: String,
        historyId: String,
        executionId: String
    ): Map<String, String> {
        val steps = toolsResultApi.listSteps(projectId, historyId, executionId).steps
        val output = HashMap<String, String>()
        steps?.forEach { step ->
            step.testExecutionStep?.toolExecution?.toolOutputs?.forEach {
                if (it.testCase != null) {
                    it.output?.fileUri?.let { it1 -> output.put(it1, it.testCase.className + '_' + it.testCase.name) }
                }
            }
        }
        return output
    }

    suspend fun getTestExecutionStepsLogcats(
        testMatrixId: String
    ): Map<String, String> {
        val testMatrix = testLabController.getTestMatrix(testMatrixId)
        testMatrix?.projectId?.let {
            testMatrix.resultStorage.toolResultsExecution?.historyId?.let { it1 ->
                testMatrix.resultStorage.toolResultsExecution?.executionId?.let { it2 ->
                    return getTestExecutionStepsLogcats(it, it1, it2)
                }
            }
        }
        return emptyMap()
    }
}
