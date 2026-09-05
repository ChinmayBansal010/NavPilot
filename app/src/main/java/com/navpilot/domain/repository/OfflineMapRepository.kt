package com.navpilot.domain.repository

import com.navpilot.domain.model.OfflineMapRegion
import kotlinx.coroutines.flow.Flow

interface OfflineMapRepository {
    suspend fun getAvailableRegions(): List<OfflineMapRegion>
    suspend fun searchRegions(query: String): List<OfflineMapRegion>
    fun getDownloadedRegions(): Flow<List<OfflineMapRegion>>
    suspend fun downloadRegion(region: OfflineMapRegion): Flow<OfflineMapRegion>
    suspend fun deleteRegion(regionId: String)
}
