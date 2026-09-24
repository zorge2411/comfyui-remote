package com.example.comfyui_remote.domain

class ExecutionProgressTracker {

    data class Snapshot(
        val overall: Float,
        val currentStep: Int,
        val maxSteps: Int,
        val completedNodes: Int,
        val totalToRun: Int
    )

    private var totalNodes = 0
    private val cached = mutableSetOf<String>()
    private val completed = mutableSetOf<String>()
    private var currentNodeId: String? = null
    private var stepFraction = 0f
    private var currentStep = 0
    private var maxSteps = 0
    private var finished = false

    fun start(totalNodes: Int) {
        this.totalNodes = totalNodes
        cached.clear()
        completed.clear()
        currentNodeId = null
        stepFraction = 0f
        currentStep = 0
        maxSteps = 0
        finished = false
    }

    fun markCached(nodeIds: Collection<String>) {
        cached.addAll(nodeIds)
    }

    fun onExecuting(nodeId: String) {
        currentNodeId?.let { if (it != nodeId) completed.add(it) }
        currentNodeId = nodeId
        stepFraction = 0f
        currentStep = 0
        maxSteps = 0
    }

    fun onStep(value: Int, max: Int) {
        currentStep = value
        maxSteps = max
        stepFraction = if (max > 0) (value.toFloat() / max).coerceIn(0f, 1f) else 0f
    }

    fun finish() {
        finished = true
    }

    fun snapshot(): Snapshot {
        val totalToRun = maxOf(totalNodes - cached.size, 1)
        val done = (completed - cached).size
        val overall = if (finished) {
            1f
        } else if (totalNodes <= 0) {
            0f
        } else {
            ((done + stepFraction) / totalToRun).coerceIn(0f, 0.99f)
        }
        return Snapshot(overall, currentStep, maxSteps, done, totalToRun)
    }
}
