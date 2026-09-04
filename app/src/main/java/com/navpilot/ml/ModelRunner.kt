package com.navpilot.ml

import com.navpilot.domain.model.ImuFrame
import com.navpilot.domain.model.NavigationEstimate
import com.navpilot.domain.model.OrientationState

interface ModelRunner {
    val isLoaded: Boolean

    suspend fun loadModel(modelName: String): Boolean

    suspend fun estimateCorrection(
        imuFrame: ImuFrame,
        orientationState: OrientationState,
        inertialEstimate: NavigationEstimate
    ): NavigationEstimate?
}

class NoOpModelRunner : ModelRunner {
    override val isLoaded: Boolean = false

    override suspend fun loadModel(modelName: String): Boolean = false

    override suspend fun estimateCorrection(
        imuFrame: ImuFrame,
        orientationState: OrientationState,
        inertialEstimate: NavigationEstimate
    ): NavigationEstimate? = null
}
