package com.pupptmstr.scooternav

import android.Manifest
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.pupptmstr.scooternav.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration.getInstance

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val FRAGMENT_TAG = "com.pupptmstr.MAP_FRAGMENT_TAG"
    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    private val requestMultiplePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.forEach {
            when {
                it.value -> {
                    // TODO() permission accepted
                }
                !shouldShowRequestPermissionRationale(it.key) -> {
                    // TODO() don't ask again
                }
                else -> {
                    // TODO() permission denied
                }
            }
            Log.e("DEBUG", "${it.key} = ${it.value}")
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        checkPermissions(permissions)

        if (supportFragmentManager.findFragmentByTag(FRAGMENT_TAG) == null) {
            supportFragmentManager.commit {
                add(R.id.map_container, MapFragment(), FRAGMENT_TAG)
            }
        }

        getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
    }

    private fun checkPermissions(permissions: Array<String>) {
        for (permission in permissions) {
            if (shouldShowRequestPermissionRationale(permission)) {
                // explain to the user why we need this permission
                break
            }
            else {
                requestMultiplePermissions.launch(permissions)
            }
        }
    }

}