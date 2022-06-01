package com.pupptmstr.scooternav

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.anastr.speedviewlib.TubeSpeedometer
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.pupptmstr.scooternav.models.PathQueryResult
import com.pupptmstr.scooternav.models.RequestPathWebsocketMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.bonuspack.routing.RoadNode
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.concurrent.atomic.AtomicBoolean


class MapFragment : Fragment() {
    private var mPrefs: SharedPreferences? = null
    private var mMapView: MapView? = null
    private var mLocationOverlay: MyLocationNewOverlay? = null
    private var mScaleBarOverlay: ScaleBarOverlay? = null
    private var btCenterMap: ImageButton? = null
    private var btFollowMe: ImageButton? = null
    private var speedometer: TubeSpeedometer? = null
    private var gpsSpeed = 0f
    private var isPrinting: AtomicBoolean = AtomicBoolean(false)
    lateinit var mapViewModel: MapViewModel
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

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        mapViewModel = ViewModelProvider(requireActivity()).get(MapViewModel::class.java)
        super.onActivityCreated(savedInstanceState)
        val context: Context = this.requireActivity().applicationContext
        speedometer = requireActivity().findViewById(R.id.speedView)
        speedometer!!.maxSpeed = 40f
        speedometer!!.setStartDegree(170)
        speedometer!!.setEndDegree(370)
        speedometer!!.withTremble = false
        //My Location
        mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mMapView)
        mMapView!!.overlays.add(mLocationOverlay)
        isPrinting.set(true)


        CoroutineScope(Dispatchers.IO).launch {
            while (isPrinting.get()) {
                mapViewModel.getCurrentSpeedAndLocation(mLocationOverlay)
                delay(2000)
            }
        }

//        CoroutineScope(Dispatchers.IO).launch {
//            getWebSocketConnection()
//        }

        //Follow me buttons
        val dm = context.resources.displayMetrics
        mScaleBarOverlay = ScaleBarOverlay(mMapView)
        mScaleBarOverlay!!.setCentred(true)
        mScaleBarOverlay!!.setScaleBarOffset(dm.widthPixels / 2, 10)
        mMapView!!.isFlingEnabled = true
        mMapView!!.overlays.add(mScaleBarOverlay)
        mLocationOverlay!!.enableMyLocation()
        mLocationOverlay!!.isOptionsMenuEnabled = true

        btCenterMap = requireActivity().findViewById(R.id.ic_center_map)
        btCenterMap!!.setOnClickListener {
            if (mLocationOverlay != null && mLocationOverlay?.myLocation != null) {
                val myPosition =
                    GeoPoint(
                        mLocationOverlay!!.myLocation.latitude,
                        mLocationOverlay!!.myLocation.longitude
                    )
                mMapView!!.controller.animateTo(myPosition)
            }
        }

        btFollowMe = requireActivity().findViewById(R.id.ic_follow_me)

        btFollowMe!!.setOnClickListener {
            if (!mLocationOverlay!!.isFollowLocationEnabled) {
                mLocationOverlay!!.enableFollowLocation()
                btFollowMe!!.setImageResource(R.drawable.ic_follow_me_on)
            } else {
                mLocationOverlay!!.disableFollowLocation()
                btFollowMe!!.setImageResource(R.drawable.ic_follow_me)
            }
        }

        //Roads
//        val roadManager: RoadManager = OSRMRoadManager(context, "MY_USER_AGENT")
//        val startPoint = GeoPoint(60.0511855, 30.44211)
//        val waypoints = ArrayList<GeoPoint>()
//        waypoints.add(startPoint)
//        val endPoint = GeoPoint(60.0582673, 30.4368031)
//        waypoints.add(endPoint)


        mapViewModel.road.observe(requireActivity()) {
            val nodeMarkers = mutableListOf<Marker>()
            for (i in 0 until it.mNodes.size) {
                val node: RoadNode = it.mNodes[i]
                val nodeMarker = Marker(mMapView)
                nodeMarker.position = node.mLocation
                nodeMarker.title = node.mInstructions
                nodeMarkers.add(nodeMarker)
            }
            val roadOverlay = RoadManager.buildRoadOverlay(it)
            for (overlay in mMapView!!.overlays) {
                if (overlay is Polyline || overlay is Marker) {
                    mMapView!!.overlays.remove(overlay)
                }
            }
            mMapView!!.overlays.add(roadOverlay)
            mMapView!!.overlays.addAll(nodeMarkers)
        }

        mapViewModel.speed.observe(requireActivity()) {
            gpsSpeed = it
            speedometer!!.speedTo(gpsSpeed, 100)
            if (mLocationOverlay!!.isFollowLocationEnabled) {
                mMapView!!.controller.animateTo(mLocationOverlay!!.myLocation)
            }
        }

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

    suspend fun printMyLocation() {
        while (isPrinting.get()) {
            if (mLocationOverlay != null && mLocationOverlay?.myLocation != null) {
                Log.i(
                    "myLocation", "\nвысота: ${mLocationOverlay!!.myLocation.altitude};\n" +
                            "широта: ${mLocationOverlay!!.myLocation.latitude};\n" +
                            "долгота: ${mLocationOverlay!!.myLocation.longitude};"
                )
                val location = mLocationOverlay!!.mMyLocationProvider.lastKnownLocation
                gpsSpeed = location.speed
                speedometer!!.speedTo(gpsSpeed, 100)
                delay(1000)
            }
        }
    }

//    override fun onLocationChanged(location: Location?, source: IMyLocationProvider?) {
//        if (mMapView == null || speedometer == null || location == null) return
//        gpsSpeed = location.speed
//        Toast.makeText(context, gpsSpeed.toString(), Toast.LENGTH_SHORT).show()
//        speedometer!!.speedTo(gpsSpeed, 100)
//        if (mLocationOverlay != null && mLocationOverlay?.myLocation != null && mLocationOverlay!!.isFollowLocationEnabled) {
//            val myPosition =
//                GeoPoint(
//                    mLocationOverlay!!.myLocation.latitude,
//                    mLocationOverlay!!.myLocation.longitude
//                )
//            mMapView!!.controller.animateTo(myPosition)
//        }
//    }

}

