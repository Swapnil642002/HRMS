package com.daksha.hrms.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.daksha.hrms.data.api.models.LocationInputDto
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LocationHelper {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result<LocationInputDto> = suspendCancellableCoroutine { continuation ->
        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellationTokenSource = CancellationTokenSource()

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.token)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val dto = LocationInputDto(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMetres = location.accuracy.toDouble(),
                        capturedAt = location.time
                    )
                    continuation.resume(Result.success(dto))
                } else {
                    continuation.resume(Result.failure(Exception("Unable to acquire location. Ensure GPS is enabled.")))
                }
            }
            .addOnFailureListener { exception ->
                continuation.resume(Result.failure(exception))
            }

        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }
    }
}
