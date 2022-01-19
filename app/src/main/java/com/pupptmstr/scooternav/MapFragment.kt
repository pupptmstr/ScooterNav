package com.pupptmstr.scooternav

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.*
import android.view.View.OnGenericMotionListener
import androidx.fragment.app.Fragment
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {
    private var mPrefs: SharedPreferences? = null
    private var mMapView: MapView? = null
    private var mLocationOverlay: MyLocationNewOverlay? = null
    private var mCompassOverlay: CompassOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

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
        mapController.setZoom(11.5)
        mMapView!!.setMultiTouchControls(true)
        mMapView!!.setOnGenericMotionListener(OnGenericMotionListener { v, event ->

            if (0 != event.source and InputDevice.SOURCE_CLASS_POINTER) {
                when (event.action) {
                    MotionEvent.ACTION_SCROLL -> {
                        if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) mMapView!!.controller.zoomOut() else {
                            //this part just centers the map on the current mouse location before the zoom action occurs
                            val iGeoPoint = mMapView!!.projection.fromPixels(
                                event.x
                                    .toInt(), event.y.toInt()
                            )
                            mMapView!!.controller.animateTo(iGeoPoint)
                            mMapView!!.controller.zoomIn()
                        }
                        return@OnGenericMotionListener true
                    }
                }
            }
            false
        })
        return mMapView as MapView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val context: Context = this.requireActivity().applicationContext
        //start settings


        //My Location
        mLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), mMapView)
        (mLocationOverlay as MyLocationNewOverlay).enableMyLocation()
        mMapView!!.overlays.add(mLocationOverlay)


        //On screen compass
        mCompassOverlay = CompassOverlay(
            context, InternalCompassOrientationProvider(context),
            mMapView
        )
        mCompassOverlay!!.enableCompass()
        mMapView!!.overlays.add(mCompassOverlay)


        //needed for pinch zooms
        mMapView!!.setMultiTouchControls(true)

        //scales tiles to the current screen's DPI, helps with readability of labels
        mMapView!!.isTilesScaledToDpi = true
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        //the rest of this is restoring the last map location the user looked at
        val zoomLevel = (mPrefs as SharedPreferences).getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 1f)
        mMapView!!.controller.setZoom(zoomLevel.toDouble())
        val orientation = (mPrefs as SharedPreferences).getFloat(PREFS_ORIENTATION, 0f)
        mMapView!!.setMapOrientation(orientation, false)
        val latitudeString = (mPrefs as SharedPreferences).getString(PREFS_LATITUDE_STRING, "1.0")
        val longitudeString = (mPrefs as SharedPreferences).getString(PREFS_LONGITUDE_STRING, "1.0")
        val latitude = java.lang.Double.valueOf(latitudeString)
        val longitude = java.lang.Double.valueOf(longitudeString)
        mMapView!!.setExpectedCenter(GeoPoint(latitude, longitude))
        setHasOptionsMenu(true)
    }

    override fun onPause() {
        //save the current location
        val edit = mPrefs!!.edit()
        edit.putString(PREFS_TILE_SOURCE, mMapView!!.tileProvider.tileSource.name())
        edit.putFloat(PREFS_ORIENTATION, mMapView!!.mapOrientation)
        edit.putString(PREFS_LATITUDE_STRING, mMapView!!.mapCenter.latitude.toString())
        edit.putString(PREFS_LONGITUDE_STRING, mMapView!!.mapCenter.longitude.toString())
        edit.putFloat(
            PREFS_ZOOM_LEVEL_DOUBLE,
            mMapView!!.zoomLevelDouble.toFloat()
        )
        edit.commit()
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
}

