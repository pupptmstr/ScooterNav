package com.pupptmstr.scooternav

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration.*
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var mapFragment: MapFragment
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);

        //handle permissions first, before map is created. not depicted here
        requestPermissionsIfNecessary(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ))

        //inflate and create the map
        setContentView(R.layout.activity_main)

        val fm = this.supportFragmentManager
        mapFragment = MapFragment.newInstance()
        fm.beginTransaction().add(R.id.map_container, mapFragment).commit();

        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));


//        //set start point and zoom by fingers
//        map = findViewById<MapView>(R.id.map_container)
//        map.setTileSource(TileSourceFactory.MAPNIK)
//        val mapController = map.controller
//        mapController.setZoom(9.5)
//        val startPoint = GeoPoint(59.9, 30.3)
//        mapController.setCenter(startPoint)
//        mapController.setZoom(11.5)
//        map.setMultiTouchControls(true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val permissionsToRequest = ArrayList<String>();
        var i = 0;
        while (i < grantResults.size) {
            permissionsToRequest.add(permissions[i]);
            i++;
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }


    private fun requestPermissionsIfNecessary(permissions: Array<String>) {
        val permissionsToRequest: ArrayList<String> = ArrayList()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                permissionsToRequest.add(permission)
            }
        }
        if (permissionsToRequest.size > 0) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toArray(arrayOfNulls(0)),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }
}