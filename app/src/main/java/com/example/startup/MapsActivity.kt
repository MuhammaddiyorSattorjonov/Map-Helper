package com.example.startup

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.get
import com.example.startup.MyLatLngObject.destination_latitude
import com.example.startup.MyLatLngObject.destination_longitude
import com.example.startup.MyLatLngObject.origin_latitude
import com.example.startup.MyLatLngObject.origin_longitude
import com.example.startup.databinding.ActivityMapsBinding
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.*
import kotlin.math.ln
import kotlin.math.pow


private const val TAG = "ZOOM"

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var mSearchView: SearchView
    lateinit var marker_place: Marker
    lateinit var marker_location: Marker

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    lateinit var locationRequest: LocationRequest

    private val DEFAULT_ZOOM = 15F
    val PERMISSION_REQUEST_ACCESS_FINE_LOCATION = 1001

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)



        if (ContextCompat.checkSelfPermission(this,
                ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {

        } else {

            ActivityCompat.requestPermissions(this,
                arrayOf(ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_ACCESS_FINE_LOCATION)
        }



        binding.more.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }

        mSearchView = binding.search


        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()

        locationRequest.interval = 1
//        locationRequest.fastestInterval = 250
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationProviderClient.requestLocationUpdates(locationRequest,
            locationCallBack,
            Looper.getMainLooper())

        binding.navView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.rate -> {
                    val appPackageName = packageName
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
//                R.id.language -> {
//                    binding.drawerLayout.closeDrawer(GravityCompat.START)
//                }
                R.id.comments -> {
                    val appPackageName = packageName
                    startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$appPackageName")))
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                }
                R.id.share -> {
                    val appPackageName = packageName
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Check out this app!")
                    shareIntent.putExtra(Intent.EXTRA_TEXT,
                        "https://play.google.com/store/apps/details?id=$appPackageName")
                    startActivity(Intent.createChooser(shareIntent, "Share via"))
                }

            }
            true
        }
        binding.voiceButton.setOnClickListener {
            startVoiceInput()
        }
        binding.resetPlace.setOnClickListener {
            binding.voiceButton.visibility = View.VISIBLE
            binding.txtSearch.text = ""
            binding.resetPlace.visibility = View.GONE
            binding.search.visibility = View.VISIBLE
            binding.search.setQuery("", false)
            binding.distanceButton.visibility = View.GONE
        }
        binding.plusPlace.setOnClickListener {
            mMap.animateCamera(CameraUpdateFactory.zoomIn())
        }
        binding.minusPlace.setOnClickListener {
            mMap.animateCamera(CameraUpdateFactory.zoomOut())
        }

    }


    var touch = true
    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap


        mMap.isMyLocationEnabled = true

        binding.findLocate.setOnClickListener {
            touch = true
            binding.findLocate.visibility = View.GONE
        }


        mMap.uiSettings.isCompassEnabled
        mMap.uiSettings.isMyLocationButtonEnabled
        mMap.setOnCameraIdleListener {
            touch = false
            binding.findLocate.visibility = View.VISIBLE
        }

        findLocation()
        searchPlaces()

        val origin = LatLng(origin_latitude, origin_longitude)
        val destination = LatLng(destination_latitude, destination_longitude)

        val urll =
            getDirectionURL(origin, destination, "AIzaSyDnv-5qJljahx-eKsOPjfgiMsIh0mll8NE")
        GetDirection(urll).execute()
    }


    private fun getDirectionURL(origin: LatLng, dest: LatLng, secret: String): String {
        return "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&sensor=false" +
                "&mode=driving" +
                "&key=$secret"
    }
    @SuppressLint("StaticFieldLeak")
    private inner class GetDirection(val url: String) :
        AsyncTask<Void, Void, List<List<LatLng>>>() {
        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body!!.string()

            val result = ArrayList<List<LatLng>>()
            try {
                val respObj = Gson().fromJson(data, GoogleMapDTO::class.java)
                val path = ArrayList<LatLng>()
                for (i in 0 until respObj.routes[0].legs[0].steps.size) {
                    path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
                }
                result.add(path)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return result
        }

        override fun onPostExecute(result: List<List<LatLng>>) {
            val lineOption = PolylineOptions()
            for (i in result.indices) {
                lineOption.addAll(result[i])
                lineOption.width(10f)
                lineOption.color(Color.BLUE)
                lineOption.geodesic(true)
            }
            mMap.addPolyline(lineOption)
        }
    }
    fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val latLng = LatLng((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }



    //find Location function
    @SuppressLint("MissingPermission")
    private fun findLocation() {
        val fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
        val location = fusedLocationProvider.lastLocation

        location.addOnSuccessListener {
            Log.d(TAG, "findLocation: $it")
            val location = it

            if (location != null) {
                var latitude = location.latitude
                val longitude = location.longitude
                val camera = CameraPosition.Builder()
                    .target(LatLng(latitude, longitude))
                    .zoom(8.5f)
                    .bearing(it.bearing)
                    .build()

                marker_place =
                    mMap.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude)))!!
                marker_location =
                    mMap.addMarker(MarkerOptions().position(LatLng(it.latitude, it.longitude)))!!
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(camera))
                origin_latitude = latitude
                origin_longitude = longitude
            } else {
                Toast.makeText(this, "Location nullPojnter", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error${it.message}", Toast.LENGTH_SHORT).show()
        }
    }
    //end function


    val locationCallBack = object : LocationCallback() {
        @SuppressLint("SuspiciousIndentation")
        override fun onLocationResult(p0: LocationResult) {
            super.onLocationResult(p0)
            Log.d(TAG,
                "onLocationResult: ${p0.lastLocation?.latitude},${p0.lastLocation?.longitude}")

            if (mMap != null) {
                if (marker_location == null) {
                    marker_location = mMap!!.addMarker(MarkerOptions().title("Bizi joylashuv")
                        .position(LatLng(p0.lastLocation?.latitude!!,
                            p0.lastLocation?.longitude!!)))!!
                } else {
                    marker_location?.position =
                        LatLng(p0.lastLocation?.latitude!!, p0.lastLocation?.longitude!!)
                }
                if (touch)
                    mMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(LatLng(
                        p0.lastLocation?.latitude!!,
                        p0.lastLocation?.longitude!!), 8.5f, 0f, p0.lastLocation?.bearing!!)))
                touch = false
            }
        }
    }


    //search function
    private fun searchPlaces() {
        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val addressList: List<Address>?
                val marker_placeOptions = MarkerOptions()

                if (query != null && query.isNotBlank()) {
                    val geocoder = Geocoder(this@MapsActivity)
                    try {
                        addressList = geocoder.getFromLocationName(query, 1)
                        if (addressList?.isNotEmpty()!!) {
                            val address = addressList[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            binding.txtSearch.text = address.featureName
                            marker_placeOptions.position(latLng)
                            marker_place.position = latLng

                            val lat_orta_arif = (destination_latitude+ origin_latitude)/2
                            val lng_orta_arif = (destination_longitude+ origin_longitude)/2




                            destination_latitude = address.latitude
                            destination_longitude = address.longitude

                            mSearchView.visibility = View.GONE
                            binding.voiceButton.visibility = View.GONE
                            binding.resetPlace.visibility = View.VISIBLE
                            binding.distanceButton.visibility = View.VISIBLE


                            //get distance
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(origin_latitude,
                                origin_longitude,
                                destination_latitude,
                                destination_longitude,
                                results)


                            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(LatLng(lat_orta_arif,lng_orta_arif),8.5f,0f,0f)))

                            val distanceInMeters = results[0]
                            val distance = distanceInMeters/1000
                            distance.toInt()
                            binding.txtDistance.text = "$distance km"
                        } else {
                            Toast.makeText(this@MapsActivity,
                                "No Results Found",
                                Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    //voice to text function
    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say place!")

        try {
            startActivityForResult(intent, 0)
        } catch (e: Exception) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 0) {
            if (resultCode == RESULT_OK && data != null) {
                val result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                val spokenText = result?.get(0)


                val addressList: List<Address>?
                val marker_placeOptions = MarkerOptions()

                if (spokenText != null && spokenText.isNotBlank()) {
                    val geocoder = Geocoder(this@MapsActivity)
                    try {
                        addressList = geocoder.getFromLocationName(spokenText, 1)
                        if (addressList?.isNotEmpty()!!) {
                            val address = addressList[0]
                            val latLng = LatLng(address.latitude, address.longitude)
                            binding.txtSearch.text = address.featureName
                            marker_placeOptions.position(latLng)
                            marker_place.position = latLng
                            mSearchView.visibility = View.GONE
                            binding.voiceButton.visibility = View.GONE
                            binding.resetPlace.visibility = View.VISIBLE
                            destination_latitude = address.latitude
                            destination_longitude = address.longitude
                            binding.distanceButton.visibility = View.VISIBLE


                            val lat_orta_arif = (destination_latitude+ origin_latitude)/2
                            val lng_orta_arif = (destination_longitude+ origin_longitude)/2

                            //
                            val lat_zoom = (origin_latitude- destination_latitude)/2
                            val lat_moduldan_chiqarish = Math.abs(lat_zoom)
                            val lat_math_pow = lat_moduldan_chiqarish.pow(2.0)

                            val lng_zoom = (origin_longitude- destination_longitude)/2
                            val lng_moduldan_chiqarish = Math.abs(lng_zoom)
                            val lng_math_pow = lng_moduldan_chiqarish.pow(2.0)

                            val lat_plus_lng = lat_math_pow+lng_math_pow

                            val lat_lng_sqrt = Math.sqrt(lat_plus_lng)

                            val sqrt_3 = Math.sqrt(3.0)

                            val zoom = lat_lng_sqrt/sqrt_3
                            Log.d(TAG, "onActivityResult: $zoom")
                            //

                            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition(LatLng(lat_orta_arif,lng_orta_arif),15f,0f,0f)))

                            //get distance
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(origin_latitude,
                                origin_longitude,
                                destination_latitude,
                                destination_longitude,
                                results)

                            val distanceInMeters = results[0]
                            val distance = distanceInMeters/1000
                            binding.txtDistance.text = "$distance km "

                        } else {
                            Toast.makeText(this@MapsActivity,
                                "No Results Found",
                                Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_ACCESS_FINE_LOCATION -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission has been granted
                    // TODO: Get the user's location
                } else {
                    // Permission has been denied
                    // TODO: Handle permission denied
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }
}