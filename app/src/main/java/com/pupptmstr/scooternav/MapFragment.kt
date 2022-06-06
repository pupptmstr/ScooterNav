package com.pupptmstr.scooternav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.LOCATION_SERVICE
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.github.anastr.speedviewlib.TubeSpeedometer
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.pupptmstr.scooternav.databinding.MapFragmentBinding
import com.pupptmstr.scooternav.models.PathQueryResult
import com.pupptmstr.scooternav.models.RequestPathWebsocketMessage
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.NetworkLocationIgnorer
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.DirectedLocationOverlay
import java.util.concurrent.atomic.AtomicBoolean


class MapFragment : Fragment(), MapEventsReceiver, View.OnClickListener, LocationListener {
    private val mapViewModel by viewModels<MapViewModel>()

    private var _binding: MapFragmentBinding? = null
    private val binding get() = _binding!!

    private var mRoads: Road? = null
    private var endPoint: GeoPoint? = null
    private var startPoint: GeoPoint? = null

    private var mSpeed = 0.0
    private var mLastTime: Long = 0
    private var mAzimuthAngleSpeed = 0.0f

    private var isStarted = false
    private var isFollowMode = false
    private val mIgnorer = NetworkLocationIgnorer()
    private val mEventsOverlay = MapEventsOverlay(this)

    private lateinit var job: Job
    private lateinit var mMapView: MapView
    private lateinit var speedometer: TubeSpeedometer
    private lateinit var mScaleBarOverlay: ScaleBarOverlay
    private lateinit var mLocationManager: LocationManager
    private lateinit var mLocationOverlay: DirectedLocationOverlay

    private var mPrefs: SharedPreferences? = null

    private var isPrinting: AtomicBoolean = AtomicBoolean(false)
    private val client = HttpClient(CIO.create {
        requestTimeout = 0
    }) {
        expectSuccess = false

        install(WebSockets) {
            pingInterval = -1L
        }
    }
    private lateinit var webSocketSession: WebSocketSession


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MapFragmentBinding.inflate(inflater, container, false)
        binding.startButton.setOnClickListener(this)
        binding.cancelButton.setOnClickListener(this)
        binding.icCenterMap.setOnClickListener(this)
        binding.icFollowMe.setOnClickListener(this)
        binding.icRotation.setOnClickListener(this)
        binding.stopTrack.setOnClickListener(this)
        mPrefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        init()
        if (savedInstanceState == null) {
            isFollowMode = false
            var location: Location? = null
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                location = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location == null)
                    location =
                        mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (location != null) {
                // get last saved location
                onLocationChanged(location)
            } else {
                //no location saved, hide overlay
                mLocationOverlay.isEnabled = false
            }
        } else {
            mLocationOverlay.location = savedInstanceState.getParcelable("location")
            startPoint = savedInstanceState.getParcelable("start")
            endPoint = savedInstanceState.getParcelable("end")
            isFollowMode = savedInstanceState.getBoolean("follow_mode")
            isStarted = savedInstanceState.getBoolean("is_started")
            mRoads = savedInstanceState.getParcelable("road")
            updateUIWithFollowMode()
            mRoads?.let { updateUIRoad(it) }
        }

        isPrinting.set(true)
//        CoroutineScope(Dispatchers.IO).launch {
//            getWebSocketConnection()
//        }

        mMapView.overlays.add(mScaleBarOverlay)
        mMapView.overlays.add(mLocationOverlay)
        mMapView.overlays.add(0, mEventsOverlay)

        mapViewModel.road.observe(viewLifecycleOwner) {
            it.getContentIfNotHandled()?.let { road ->
                if (road.mNodes.isNotEmpty()) {
                    mRoads = road
                    updateUIRoad(road)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Can't find path for scooter, trying to find pedestrian path",
                        Toast.LENGTH_LONG
                    ).show()
                    getPedestrianRoad(startPoint!!, endPoint!!)
                }
            }
        }

        setHasOptionsMenu(true)
        super.onViewCreated(view, savedInstanceState)
    }

    private fun updateUIRoad(road: Road) {
        val nodeMarkers = getRoadMarkers(road)
        val roadOverlay = RoadManager.buildRoadOverlay(road, Color.BLUE, 10.0f)
        removeMarkersAndPolyline()
        mMapView.overlays.add(roadOverlay)
        mMapView.overlays.addAll(nodeMarkers)
        mMapView.invalidate()
        if (!isStarted) {
            try {
                mMapView.zoomToBoundingBox(road.mBoundingBox, true, 200)
            } catch (ex: IllegalArgumentException) {
                Log.i("ZOOM_BOUNDING_BOX", "try to zoom after screen rotation")
            }
            binding.visibility = true
        }
    }

    private fun getRoadMarkers(road: Road): MutableList<Marker> {
        val nodeMarkers = mutableListOf<Marker>()
        if (road.mNodes.isNotEmpty()) {
            val node = road.mNodes[road.mNodes.indices.last]
            val nodeMarker = Marker(mMapView).apply {
                position = node.mLocation
                title = node.mInstructions
            }
            nodeMarkers.add(nodeMarker)
        }
        return nodeMarkers
    }

    private fun savePrefs() {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ed = (mPrefs as SharedPreferences).edit()
        val c = mMapView.mapCenter as GeoPoint
        ed.putString(CENTER_LATITUDE, c.latitude.toString())
        ed.putString(CENTER_LONGITUDE, c.longitude.toString())
        ed.putFloat(ZOOM_LEVEL, mMapView.zoomLevelDouble.toFloat())
        ed.putFloat(ORIENTATION, mMapView.mapOrientation)
        ed.apply()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable("location", mLocationOverlay.location)
        outState.putBoolean("follow_mode", isFollowMode)
        outState.putParcelable("start", startPoint)
        outState.putParcelable("end", endPoint)
        outState.putParcelable("road", mRoads)
        outState.putBoolean("is_started", isStarted)
        savePrefs()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun init() {
        // saved map params
        val zoomLevel = (mPrefs as SharedPreferences).getFloat("ZOOM_LEVEL", 9.5f).toDouble()
        val orientation = (mPrefs as SharedPreferences).getFloat("ORIENTATION", 0f)
        val latitudeString = (mPrefs as SharedPreferences).getString("CENTER_LATITUDE_St", "59.9")
        val longitudeString = (mPrefs as SharedPreferences).getString("CENTER_LONGITUDE_St", "30.3")
        val latitude = java.lang.Double.valueOf(latitudeString!!)
        val longitude = java.lang.Double.valueOf(longitudeString!!)

        // Map
        mMapView = binding.map.apply {
            controller.setCenter(GeoPoint(latitude, longitude))
            controller.setZoom(zoomLevel)
            mapOrientation = orientation
            setDestroyMode(false)
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            isFlingEnabled = true
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_MOVE && isFollowMode) {
                    isFollowMode = false
                    updateUIWithFollowMode()
                }
                false
            }
        }

        // speedometer
        speedometer = binding.speedView.apply {
            maxSpeed = 40f
            withTremble = false
            setStartDegree(170)
            setEndDegree(370)
        }
        // Scale bar
        val dm = requireContext().resources.displayMetrics
        mScaleBarOverlay = ScaleBarOverlay(mMapView).apply {
            setCentred(true)
            setScaleBarOffset(dm.widthPixels / 2, 10)
        }

        //My Location overlay
        mLocationOverlay = DirectedLocationOverlay(requireContext())
        mLocationManager = requireActivity().getSystemService(LOCATION_SERVICE) as LocationManager
    }

    override fun singleTapConfirmedHelper(endPoint: GeoPoint): Boolean {
        if (!isStarted) {
            if (mLocationOverlay.location != null) {
                val startPoint = GeoPoint(
                    mLocationOverlay.location.latitude,
                    mLocationOverlay.location.longitude
                )
                getRoad(startPoint, endPoint)
            }
        }
        return true
    }

    private fun getRoad(startPoint: GeoPoint, endPoint: GeoPoint) {
        this@MapFragment.startPoint = startPoint
        this@MapFragment.endPoint = endPoint
        val waypoints = arrayListOf(startPoint, endPoint)
        val roadManager: RoadManager = OSRMRoadManager(context, "MY_USER_AGENT")
        lifecycleScope.launch {
            mapViewModel.getPath(roadManager, waypoints)
        }
    }

    private fun getPedestrianRoad(startPoint: GeoPoint, endPoint: GeoPoint) {
        this@MapFragment.startPoint = startPoint
        this@MapFragment.endPoint = endPoint
        val waypoints = arrayListOf(startPoint, endPoint)
        val roadManager: RoadManager = OSRMRoadManager(context, "MY_USER_AGENT")
        lifecycleScope.launch {
            mapViewModel.getPedestrianPath(roadManager, waypoints)
        }
    }

    override fun longPressHelper(p: GeoPoint?): Boolean {
        return false
    }

    override fun onResume() {
        super.onResume()
        if (isStarted) {
            job = updateRoad()
            job.start()
        }
        val isOneProviderEnabled: Boolean = startLocationUpdates()
        mLocationOverlay.isEnabled = isOneProviderEnabled
        mMapView.onResume()
    }

    override fun onPause() {
        job.cancel()
        if (ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mLocationManager.removeUpdates(this)
        }
        savePrefs()
        mMapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mMapView.onDetach()
        _binding = null
    }

    private fun startLocationUpdates(): Boolean {
        var result = false
        for (provider in mLocationManager.getProviders(true)) {
            if (ContextCompat.checkSelfPermission(
                    requireActivity(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mLocationManager.requestLocationUpdates(
                    provider!!,
                    (2 * 1000).toLong(),
                    0.0f,
                    this
                )
                result = true
            }
        }
        return result
    }

    companion object {
        private const val PREFS_NAME = "org.andnav.osm.prefs"
        private const val CENTER_LATITUDE = "center_latitude"
        private const val CENTER_LONGITUDE = "center_longitude"
        private const val ZOOM_LEVEL = "zoom_level"
        private const val ORIENTATION = "orientation"
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
                    if (mLocationOverlay.location != null) {
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

    override fun onClick(v: View) {
        when (v.id) {
            R.id.start_button -> startTrack()
            R.id.cancel_button -> cancelTrack()
            R.id.ic_center_map -> centerMap()
            R.id.ic_follow_me -> followMode()
            R.id.ic_rotation -> rotateMap()
            R.id.stop_track -> stopTrack()
        }
    }

    private fun stopTrack() {
        job.cancel()
        isStarted = false
        mRoads = null
        startPoint = null
        endPoint = null
        binding.visibility2 = false
        removeMarkersAndPolyline()
    }

    private fun rotateMap() {
        isFollowMode = false
        updateUIWithFollowMode()
        mMapView.controller.animateTo(
            mMapView.mapCenter,
            mMapView.zoomLevelDouble,
            null,
            0.0f
        )
    }


    private fun startTrack() {
        job = updateRoad()
        job.start()
        isStarted = true
        binding.visibility2 = true
        if (!isFollowMode) {
            isFollowMode = !isFollowMode
            updateUIWithFollowMode()
        }
        mMapView.invalidate()
        binding.visibility = false
    }

    private fun cancelTrack() {
        mRoads = null
        binding.visibility = false
        removeMarkersAndPolyline()
    }

    private fun centerMap() {
        if (mLocationOverlay.location != null) {
            val myPosition =
                GeoPoint(
                    mLocationOverlay.location.latitude,
                    mLocationOverlay.location.longitude
                )
            mMapView.controller.animateTo(myPosition, 16.5, 2500)
        }
    }

    private fun followMode() {
        isFollowMode = !isFollowMode
        updateUIWithFollowMode()
    }

    private fun removeMarkersAndPolyline() {
        for (overlay in mMapView.overlays) {
            if (overlay is Polyline || overlay is Marker) {
                mMapView.overlays.remove(overlay)
                InfoWindow.closeAllInfoWindowsOn(mMapView)
            }
        }
        mMapView.invalidate()
    }

    private fun updateUIWithFollowMode() {
        if (isFollowMode) {
            binding.icFollowMe.setBackgroundResource(R.drawable.osm_ic_follow_me_on)
            if (mLocationOverlay.isEnabled && mLocationOverlay.location != null) {
                mMapView.controller.animateTo(
                    mLocationOverlay.location,
                    17.0,
                    null,
                    -mAzimuthAngleSpeed
                )
            }
        } else {
            binding.icFollowMe.setBackgroundResource(R.drawable.osm_ic_follow_me)
        }
    }

    override fun onLocationChanged(location: Location) {
        val currentTime = System.currentTimeMillis()
        if (mIgnorer.shouldIgnore(location.provider, currentTime)) return
        val dT = (currentTime - mLastTime).toDouble()
        if (dT < 100.0) return

        mLastTime = currentTime
        val newLocation = GeoPoint(location)
        if (!mLocationOverlay.isEnabled) {
            //we get the location for the first time:
            mLocationOverlay.isEnabled = true
            mMapView.controller.animateTo(newLocation)
        }
        val prevLocation = mLocationOverlay.location
        mLocationOverlay.location = newLocation
        mLocationOverlay.setAccuracy(location.accuracy.toInt())
        if (isStarted) {
            val userPoint = mLocationOverlay.location
            val polyline = RoadManager.buildRoadOverlay(mRoads, Color.BLUE, 10.0f)
            var minDistance = Double.MAX_VALUE
            var index = 0
            for (i in 0 until polyline.actualPoints.size) {
                if (userPoint.distanceToAsDouble(polyline.actualPoints[i]) < minDistance) {
                    minDistance = userPoint.distanceToAsDouble(polyline.actualPoints[i])
                    index = i
                }
            }
            if (index == polyline.actualPoints.size - 1) {
                Toast.makeText(
                    requireActivity(),
                    "Вы достигли точки назначения", Toast.LENGTH_SHORT
                ).show()
                stopTrack()
                return
            }
            val linePoint1 = polyline.actualPoints[index]
            val linePoint2 = polyline.actualPoints[index + 1]
            val nearestPoint = nearestPointOnSegment(userPoint, linePoint1, linePoint2)

            if (userPoint.distanceToAsDouble(nearestPoint) > 100) {
                Toast.makeText(
                    requireActivity(),
                    "Вы сошли с маршрута", Toast.LENGTH_SHORT
                ).show()
                getRoad(userPoint, endPoint!!)
            } else if (nearestPoint.latitude != linePoint1.latitude && nearestPoint.longitude != linePoint1.longitude) {
                polyline.actualPoints[index] = nearestPoint
                polyline.setPoints(polyline.actualPoints.drop(index))
                startPoint = polyline.actualPoints[0]
                updateUIPolyline(polyline)
            }
        }

        if (prevLocation != null && location.provider == LocationManager.GPS_PROVIDER) {
            mSpeed = location.speed * 3.6
            speedometer.speedTo(mSpeed.toFloat(), 100)

            // TODO: check if speed is not too small
            if (mSpeed >= 0.1) {
                mAzimuthAngleSpeed = location.bearing
                mLocationOverlay.setBearing(mAzimuthAngleSpeed)
            }
        }
        if (isFollowMode) {
            //keep the map view centered on current location:
            mMapView.controller.animateTo(newLocation, 17.0, null, -mAzimuthAngleSpeed)
        } else {
            //just redraw the location overlay:
            mMapView.invalidate()
        }
    }

    private fun updateRoad(): Job {
        return lifecycleScope.launch {
            while (isStarted) {
                delay(1000 * 60 * 3)
                getRoad(mLocationOverlay.location, endPoint!!)
            }
        }
    }

    private fun updateUIPolyline(polyline: Polyline) {
        for (overlay in mMapView.overlays) {
            if (overlay is Polyline) {
                mMapView.overlays.remove(overlay)
            }
        }
        mMapView.overlays.add(polyline)
        mMapView.invalidate()
    }

    private fun nearestPointOnSegment(
        point: GeoPoint,
        point1: GeoPoint,
        point2: GeoPoint
    ): GeoPoint {
        val a = point.latitude - point1.latitude
        val b = point.longitude - point1.longitude
        val c = point2.latitude - point1.latitude
        val d = point2.longitude - point1.longitude
        val dot = a * c + b * d
        val len = c * c + d * d
        val param: Double = if (len != 0.0) {
            dot / len
        } else {
            -1.0
        }
        val xx: Double
        val yy: Double
        when {
            param < 0 -> {
                xx = point1.latitude
                yy = point1.longitude
            }
            param > 1 -> {
                xx = point2.latitude
                yy = point2.longitude
            }
            else -> {
                xx = point1.latitude + param * c
                yy = point1.longitude + param * d
            }
        }
        return GeoPoint(xx, yy)
    }
}

