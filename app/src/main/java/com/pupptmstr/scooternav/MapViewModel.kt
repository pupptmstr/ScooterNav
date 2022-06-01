package com.pupptmstr.scooternav

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapViewModel : ViewModel() {

    val road = MutableLiveData<Road>()
    val speed = MutableLiveData<Float>()
    private var previousLocation = GeoPoint(0.0, 0.0)


    suspend fun getPath(roadManager: RoadManager, waypoints: ArrayList<GeoPoint>) {
        road.postValue(
            withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
                (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT)
                roadManager.getRoad(waypoints)
            }
        )
    }

    suspend fun getCurrentSpeedAndLocation(mLocationOverlay: MyLocationNewOverlay?) {
        if (mLocationOverlay != null && mLocationOverlay.myLocation != null) {
//            Log.i(
//                "myLocation", "\nвысота: ${mLocationOverlay.myLocation.altitude};\n" +
//                        "широта: ${mLocationOverlay.myLocation.latitude};\n" +
//                        "долгота: ${mLocationOverlay.myLocation.longitude};"
//            )
            val location = mLocationOverlay.mMyLocationProvider.lastKnownLocation
            if (speed.value != location.speed || previousLocation.latitude != mLocationOverlay.myLocation.latitude || previousLocation.longitude != mLocationOverlay.myLocation.longitude) {
                speed.postValue(location.speed)
                previousLocation = mLocationOverlay.myLocation
            } else {
                speed.postValue(0f)
            }
        }
    }
}