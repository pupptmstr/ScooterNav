package com.pupptmstr.scooternav

import androidx.lifecycle.LiveData
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

    private val _road: MutableLiveData<Event<Road>> = MutableLiveData()
    val road: LiveData<Event<Road>>
        get() = _road

    suspend fun getPath(roadManager: RoadManager, waypoints: ArrayList<GeoPoint>) {
        _road.value = Event(
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                (roadManager as OSRMRoadManager).setMean("")
                roadManager.setService("http://pupptmstr.simsim.ftp.sh:4115/api/v1/map/path/")
                roadManager.getRoad(waypoints)
            }
        )
    }

    suspend fun getPedestrianPath(roadManager: RoadManager, waypoints: ArrayList<GeoPoint>) {
        _road.value = Event(
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT)
                roadManager.getRoad(waypoints)
            }
        )
    }

}