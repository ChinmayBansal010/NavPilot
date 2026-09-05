package com.navpilot.data.repository

import android.content.Context
import com.navpilot.domain.model.BoundingBox
import com.navpilot.domain.model.OfflineMapRegion
import com.navpilot.domain.model.OfflineMapStatus
import com.navpilot.domain.model.SearchResultType
import com.navpilot.domain.repository.OfflineMapRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class OsmOfflineMapRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient()
) : OfflineMapRepository {

    private val downloadedRegionsFlow = MutableStateFlow<List<OfflineMapRegion>>(emptyList())
    private val root = File(context.filesDir, "offline_maps").apply { mkdirs() }
    private val registryFile = File(root, "registry.json")

    init {
        loadStoredRegions()
    }

    override suspend fun getAvailableRegions(): List<OfflineMapRegion> = withContext(Dispatchers.IO) {
        val states = listOf(
            "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat",
            "Haryana", "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh",
            "Maharashtra", "Manipur", "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab",
            "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana", "Tripura", "Uttar Pradesh",
            "Uttarakhand", "West Bengal"
        )
        val uts = listOf(
            "Andaman and Nicobar Islands", "Chandigarh", "Dadra and Nagar Haveli and Daman and Diu",
            "Delhi", "Jammu and Kashmir", "Ladakh", "Lakshadweep", "Puducherry"
        )

        val allNames = (states + uts).sorted()
        allNames.map { name ->
            val id = name.lowercase(Locale.ROOT).replace(" ", "_")
            val stored = downloadedRegionsFlow.value.find { it.id == id }
            stored ?: OfflineMapRegion(
                id = id,
                name = name,
                description = if (name in uts) "Union Territory, India" else "State, India",
                boundingBox = BoundingBox(0.0, 0.0, 0.0, 0.0),
                type = if (name in uts) SearchResultType.MAP_REGION else SearchResultType.STATE,
                status = OfflineMapStatus.AVAILABLE
            )
        }
    }

    override suspend fun searchRegions(query: String): List<OfflineMapRegion> {
        if (query.isBlank()) return getAvailableRegions()
        return getAvailableRegions().filter { it.name.contains(query, ignoreCase = true) }
    }

    override fun getDownloadedRegions(): Flow<List<OfflineMapRegion>> = downloadedRegionsFlow

    override suspend fun downloadRegion(region: OfflineMapRegion): Flow<OfflineMapRegion> = flow {
        val realRegion = if (region.boundingBox.minLat == 0.0) {
            fetchRegionMetadata(region)
        } else {
            region
        }

        emit(realRegion.copy(status = OfflineMapStatus.DOWNLOADING, downloadProgress = 0.05f))

        for (i in 1..20) {
            delay(100L.milliseconds)
            emit(realRegion.copy(status = OfflineMapStatus.DOWNLOADING, downloadProgress = i / 20f))
        }

        val finalizedRegion = realRegion.copy(
            status = OfflineMapStatus.DOWNLOADED,
            downloadProgress = 1f,
            localPath = File(root, "${realRegion.id}.navmap").absolutePath
        )
        
        saveRegionLocally(finalizedRegion)
        
        downloadedRegionsFlow.update { current ->
            (current.filter { it.id != finalizedRegion.id } + finalizedRegion)
        }
        
        emit(finalizedRegion)
    }

    override suspend fun deleteRegion(regionId: String) {
        val region = downloadedRegionsFlow.value.find { it.id == regionId }
        region?.localPath?.let { File(it).delete() }
        downloadedRegionsFlow.update { it.filter { r -> r.id != regionId } }
        persistRegistry()
    }

    private suspend fun fetchRegionMetadata(region: OfflineMapRegion): OfflineMapRegion = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search?q=${region.name},+India&format=json&limit=1"
        val request = Request.Builder().url(url).header("User-Agent", "NavPilot").build()
        
        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext region
            val jsonArray = Json.parseToJsonElement(body).jsonArray
            if (jsonArray.isEmpty()) return@withContext region
            
            val obj = jsonArray[0].jsonObject
            val bboxArr = obj["boundingbox"]?.jsonArray
            if (bboxArr != null && bboxArr.size == 4) {
                val minLat = bboxArr[0].jsonPrimitive.content.toDouble()
                val maxLat = bboxArr[1].jsonPrimitive.content.toDouble()
                val minLon = bboxArr[2].jsonPrimitive.content.toDouble()
                val maxLon = bboxArr[3].jsonPrimitive.content.toDouble()
                return@withContext region.copy(
                    boundingBox = BoundingBox(minLat, maxLat, minLon, maxLon)
                )
            }
        } catch (_: Exception) {}
        region
    }

    private fun loadStoredRegions() {
        try {
            val stored = root.listFiles { _, name -> name.endsWith(".navmap") }?.map { file ->
                val name = file.nameWithoutExtension.replace("_", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                
                OfflineMapRegion(
                    id = file.nameWithoutExtension,
                    name = name,
                    description = "Offline map downloaded",
                    boundingBox = BoundingBox(0.0, 0.0, 0.0, 0.0),
                    type = SearchResultType.MAP_REGION,
                    status = OfflineMapStatus.DOWNLOADED,
                    localPath = file.absolutePath
                )
            } ?: emptyList()
            downloadedRegionsFlow.value = stored
        } catch (_: Exception) {}
    }

    private fun saveRegionLocally(region: OfflineMapRegion) {
        val file = File(root, "${region.id}.navmap")
        file.writeText("region_id=${region.id}\nbbox=${region.boundingBox.minLat},${region.boundingBox.maxLat},${region.boundingBox.minLon},${region.boundingBox.maxLon}")
        persistRegistry()
    }

    private fun persistRegistry() {
        try {
            registryFile.writeText("{\"downloaded\": ${downloadedRegionsFlow.value.size}}")
        } catch (_: Exception) {}
    }
}
