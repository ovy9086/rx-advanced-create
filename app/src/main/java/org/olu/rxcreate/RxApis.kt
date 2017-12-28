package org.olu.rxcreate

import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class UserData(val location: Location,
                    val azimuth: Double)

interface RxAndroidApis {

    fun getLocationStream(): Flowable<Location>

    fun getDeviceMagneticField(): Flowable<FloatArray>

    fun getDeviceAcceleration(): Flowable<FloatArray>
}

interface RxRetrofitApi {

    @POST("/submitUser")
    fun postUserData(@Body userData: UserData): Flowable<ResponseBody>
}


class UserService(
        val locationStream: Flowable<Location>,
        val accelerometerStream: Flowable<FloatArray>,
        val magneticFieldStream: Flowable<FloatArray>,
        val restApi: RxRetrofitApi) {

    fun startTrackingUserData() {

        val azimuthStream = Flowable.combineLatest(
                accelerometerStream,
                magneticFieldStream,
                computeAzimuth())

        Flowable.combineLatest(
                locationStream.filter { it.accuracy < 20 },
                azimuthStream,
                combineLocationAndAzimuth())
                .throttleLast(5, TimeUnit.SECONDS)
                .flatMap { userData -> restApi.postUserData(userData) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    //API response received
                })

    }

    private fun combineLocationAndAzimuth() = BiFunction<Location, Double, UserData> { location, azimuth ->
        UserData(location, azimuth)
    }

    private fun computeAzimuth() = BiFunction<FloatArray, FloatArray, Double> { acceleration, magnetic ->
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, acceleration, magnetic)
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            return@BiFunction Math.toDegrees(orientation.first().toDouble())
        }
        return@BiFunction Double.MIN_VALUE
    }
}