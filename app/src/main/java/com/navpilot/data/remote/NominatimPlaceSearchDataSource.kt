package com.navpilot.data.remote

import com.navpilot.domain.model.DestinationSearchResult
import com.navpilot.domain.model.GeoPosition
import com.navpilot.domain.model.SearchResultType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

class NominatimPlaceSearchDataSource(
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<DestinationSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=10&countrycodes=in"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "NavPilot-Android-App")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            
            val jsonArray = Json.parseToJsonElement(body).jsonArray
            jsonArray.map { element ->
                val obj = element.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val lon = obj["lon"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
                val displayName = obj["display_name"]?.jsonPrimitive?.content ?: ""
                val type = mapType(obj["type"]?.jsonPrimitive?.content ?: "", obj["class"]?.jsonPrimitive?.content ?: "")
                
                DestinationSearchResult(
                    id = obj["place_id"]?.jsonPrimitive?.content ?: displayName,
                    title = displayName.substringBefore(","),
                    subtitle = displayName.substringAfter(",").trim(),
                    position = GeoPosition(lat, lon),
                    type = type
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun mapType(type: String, clazz: String): SearchResultType {
        return when (clazz) {
            "boundary" -> if (type == "administrative") SearchResultType.STATE else SearchResultType.MAP_REGION
            "place" -> {
                when (type) {
                    "country" -> SearchResultType.COUNTRY
                    "state" -> SearchResultType.STATE
                    "city" -> SearchResultType.CITY
                    "town" -> SearchResultType.TOWN
                    "village" -> SearchResultType.VILLAGE
                    else -> SearchResultType.PLACE
                }
            }
            "highway" -> SearchResultType.ROAD
            "amenity", "shop", "tourism", "leisure" -> SearchResultType.PLACE
            "building" -> SearchResultType.ADDRESS
            else -> SearchResultType.PLACE
        }
    }
}
