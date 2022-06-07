package com.pupptmstr.scooternav

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.pupptmstr.scooternav.models.Coordinates
import com.pupptmstr.scooternav.models.DataWebSocketMessage
import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import java.util.concurrent.atomic.AtomicBoolean

class MapViewModel : ViewModel() {

    private val _road: MutableLiveData<Event<Road>> = MutableLiveData()
    val road: LiveData<Event<Road>>
        get() = _road
    private var webSocket: WebSocketSession? = null
    private val isStarted = AtomicBoolean()

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

    fun closeWebSocket() {
        isStarted.set(false)
    }

    suspend fun sendSpeedAndGeoData(speed: Double, node1: Coordinates, node2: Coordinates) {
        if (webSocket != null) {
            val gson =
                GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create()
            val webSocketMessage = DataWebSocketMessage(speed, node1, node2)
            val message = gson.toJson(webSocketMessage)
            Log.i("speedAndGeo", message)
            webSocket!!.send(message)
        }
    }


    suspend fun getWebSocketConnection(client: HttpClient) {
        isStarted.set(true)
        viewModelScope.launch {
            client.ws(
                method = HttpMethod.Get,
                host = "pupptmstr.simsim.ftp.sh",
                port = 4115,
                path = "/api/v1/client"
            ) {
                try {
                    send(Frame.Text("connection"))
                    webSocket = this
                    while (isStarted.get()) {
                        //just work until closed
                        delay(1000)
                    }
                    this.close(CloseReason(CloseReason.Codes.NORMAL, "Finished riding"))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

}