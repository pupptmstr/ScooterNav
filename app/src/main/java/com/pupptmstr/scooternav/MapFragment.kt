package com.pupptmstr.scooternav

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.pupptmstr.scooternav.models.PathQueryResult
import com.pupptmstr.scooternav.models.RequestPathWebsocketMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.routing.RoadNode
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.concurrent.atomic.AtomicBoolean


class MapFragment : Fragment() {
    private var mPrefs: SharedPreferences? = null
    private var mMapView: MapView? = null
    private var mLocationOverlay: MyLocationNewOverlay? = null
    private var mCompassOverlay: CompassOverlay? = null
    private var isPrinting: AtomicBoolean = AtomicBoolean(false)
    private val client = HttpClient(CIO.create {
        requestTimeout = 0
    }) {
        expectSuccess = false

        install(WebSockets) {
            pingInterval = -1L
        }
    }
    lateinit var webSocketSession: WebSocketSession

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mMapView = MapView(inflater.context)
        mMapView!!.setDestroyMode(false)
        mMapView!!.setTileSource(TileSourceFactory.MAPNIK)
        val mapController = mMapView!!.controller
        mapController.setZoom(9.5)
        val startPoint = GeoPoint(59.9, 30.3)
        mapController.setCenter(startPoint)
        return mMapView!!
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val context: Context = this.requireActivity().applicationContext

        //My Location
        mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mMapView)
        mLocationOverlay!!.enableMyLocation()
        mMapView!!.overlays.add(mLocationOverlay)
        isPrinting.set(true)
        CoroutineScope(Dispatchers.IO).launch {
            printMyLocation()
        }

        CoroutineScope(Dispatchers.IO).launch {
            getWebSocketConnection()
        }


        //On screen compass
        mCompassOverlay = CompassOverlay(
            context, InternalCompassOrientationProvider(context),
            mMapView
        )
        mCompassOverlay!!.enableCompass()
        mMapView!!.overlays.add(mCompassOverlay)

//        //marker overlay
//        val marker = Marker(mMapView)
//        marker.position = GeoPoint(59.9, 30.3)
//        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
//        marker.title = "Start point"
//        mMapView!!.overlays.add(marker)

        //Roads
        val roadManager: RoadManager = OSRMRoadManager(context, "MY_USER_AGENT")
        val startPoint = GeoPoint(60.0511855, 30.44211)
        val waypoints = ArrayList<GeoPoint>()
        waypoints.add(startPoint)
        val endPoint = GeoPoint(60.0582673, 30.4368031)
        waypoints.add(endPoint)


        val road = CoroutineScope(Dispatchers.IO).async {
            (roadManager as OSRMRoadManager).setMean(OSRMRoadManager.MEAN_BY_FOOT)
            roadManager.getRoad(waypoints)
        }

        while (road.isActive) Unit

        for (i in 0 until road.getCompleted().mNodes.size) {
            val node: RoadNode = road.getCompleted().mNodes[i]
            val nodeMarker = Marker(mMapView)
            nodeMarker.position = node.mLocation
            nodeMarker.title = node.mInstructions
            mMapView!!.overlays.add(nodeMarker)
        }
        val roadOverlay = RoadManager.buildRoadOverlay(road.getCompleted())
        mMapView!!.overlays.add(roadOverlay)

        //needed for pinch zooms
        mMapView!!.setMultiTouchControls(true)

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMapView!!.isTilesScaledToDpi = true
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

//        //the rest of this is restoring the last map location the user looked at
//        val zoomLevel = (mPrefs as SharedPreferences).getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 1f)
//        mMapView!!.controller.setZoom(zoomLevel.toDouble())
//        val orientation = (mPrefs as SharedPreferences).getFloat(PREFS_ORIENTATION, 0f)
//        mMapView!!.setMapOrientation(orientation, false)
//        val latitudeString = (mPrefs as SharedPreferences).getString(PREFS_LATITUDE_STRING, "1.0")
//        val longitudeString = (mPrefs as SharedPreferences).getString(PREFS_LONGITUDE_STRING, "1.0")
//        val latitude = java.lang.Double.valueOf(latitudeString)
//        val longitude = java.lang.Double.valueOf(longitudeString)
//        mMapView!!.setExpectedCenter(GeoPoint(latitude, longitude))
//        setHasOptionsMenu(true)
    }

    override fun onPause() {
//        //save the current location
//        val edit = mPrefs!!.edit()
//        edit.putString(PREFS_TILE_SOURCE, mMapView!!.tileProvider.tileSource.name())
//        edit.putFloat(PREFS_ORIENTATION, mMapView!!.mapOrientation)
//        edit.putString(PREFS_LATITUDE_STRING, mMapView!!.mapCenter.latitude.toString())
//        edit.putString(PREFS_LONGITUDE_STRING, mMapView!!.mapCenter.longitude.toString())
//        edit.putFloat(
//            PREFS_ZOOM_LEVEL_DOUBLE,
//            mMapView!!.zoomLevelDouble.toFloat()
//        )
//        edit.commit()
        mMapView!!.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mMapView!!.onDetach()
    }

    override fun onResume() {
        super.onResume()
        mMapView!!.onResume()
    }

    fun invalidateMapView() {
        mMapView!!.invalidate()
    }

    suspend fun printMyLocation() {
        while (isPrinting.get()) {
            if (mLocationOverlay != null && mLocationOverlay?.myLocation != null) {
                Log.i(
                    "myLocation", "\nвысота: ${mLocationOverlay!!.myLocation.altitude};\n" +
                            "широта: ${mLocationOverlay!!.myLocation.latitude};\n" +
                            "долгота: ${mLocationOverlay!!.myLocation.longitude};"
                )
                delay(5000)
            }
        }
    }

    private suspend fun getWebSocketConnection() {
        val gson = GsonBuilder().setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create()
        client.ws(
            method = HttpMethod.Get,
            host = "pupptmstr.simsim.ftp.sh",
            port = 4115,
            path = "/api/v1/client"
        ) {
            try {
                webSocketSession = this
                while (isPrinting.get()) {
                    if (mLocationOverlay != null && mLocationOverlay?.myLocation != null) {
                        val pathRequest = RequestPathWebsocketMessage(334409, 9724356897)
                        val message = gson.toJson(pathRequest).toString()
                        send(Frame.Text(message))
                        when (val respond = incoming.receive()) {
                            is Frame.Text -> {
                                val respondMessage = respond.readText()
                                val respondBody =
                                    gson.fromJson(respondMessage, PathQueryResult::class.java)
                                Log.i("myPath", "Path length = ${respondBody.totalLength}")
                            }

                            else -> {}
                        }
                        delay(15000)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "org.andnav.osm.prefs"
        private const val PREFS_TILE_SOURCE = "tilesource"
        private const val PREFS_LATITUDE_STRING = "latitudeString"
        private const val PREFS_LONGITUDE_STRING = "longitudeString"
        private const val PREFS_ORIENTATION = "orientation"
        private const val PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble"
        private const val MENU_ABOUT = Menu.FIRST + 1
        private const val MENU_LAST_ID = MENU_ABOUT + 1 // Always set to last unused id
        fun newInstance(): MapFragment {
            return MapFragment()
        }
    }
}

