package dev.androidx.ci.testRunner

import dev.androidx.ci.firebase.ToolsResultApi
import dev.androidx.ci.generated.ftl.TestMatrix
import dev.androidx.ci.generated.testResults.Step

internal class TestExecutionStore(
    private val toolsResultApi: ToolsResultApi
) {
    private suspend fun getTestExecutionSteps(
        projectId: String,
        historyId: String,
        executionId: String
    ): List<Step>? {
        return toolsResultApi.listSteps(projectId, historyId, executionId).steps
    }

    suspend fun getTestExecutionSteps(
        testMatrix: TestMatrix
    ): List<Step>? {
        testMatrix?.projectId?.let { projectId ->
            testMatrix.resultStorage.toolResultsExecution?.historyId?.let { historyId ->
                testMatrix.resultStorage.toolResultsExecution?.executionId?.let { executionId ->
                    return getTestExecutionSteps(projectId, historyId, executionId)
                }
            }
        }
        return emptyList()
    }
}
