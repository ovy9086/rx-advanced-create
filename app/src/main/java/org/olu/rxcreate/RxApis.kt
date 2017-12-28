package org.olu.rxcreate

import android.location.Location
import io.reactivex.Observable
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

    fun getLocationStream(): Observable<Location>

    fun getAzimuthStream(): Observable<Double>
}

interface RxRestApi {

    @POST("/submitUser")
    fun postUserData(@Body userData: UserData): Observable<ResponseBody>
}


class UserService(
        val locationStream: Observable<Location>,
        val azimuthStream: Observable<Double>,
        val restApi: RxRestApi) {

    private val combineDataFunction = BiFunction<Location, Double, UserData> { location, azimuth ->
        UserData(location, azimuth)
    }

    fun startTrackingUserData() {
        Observable.combineLatest(
                locationStream.filter { it.accuracy < 20 },
                azimuthStream,
                combineDataFunction)
                .throttleLast(5, TimeUnit.SECONDS)
                .flatMap { userData -> restApi.postUserData(userData) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ response ->
                    //Data successfully sent
                }, { error ->
                    //Handle error
                })

    }
}