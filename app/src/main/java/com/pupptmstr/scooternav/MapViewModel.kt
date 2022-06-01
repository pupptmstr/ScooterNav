package com.pupptmstr.scooternav

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint

class MapViewModel : ViewModel() {

    val liveData = MutableLiveData<Road>()

    suspend fun getPath(roadManager: RoadManager, waypoints: ArrayList<GeoPoint>) {
        liveData.postValue(
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT)
                roadManager.getRoad(waypoints)
            }
        )
    }
}