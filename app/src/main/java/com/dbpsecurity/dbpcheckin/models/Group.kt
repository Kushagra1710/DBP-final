package com.dbpsecurity.dbpcheckin.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Group(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("latitude")
    val latitude: Double,

    @SerialName("longitude")
    val longitude: Double,

    @SerialName("start_time")
    val startTime: String,

    @SerialName("end_time")
    val endTime: String,

    @SerialName("radius")
    val radius: Double = 100.0,

    @SerialName("is_location_restricted")
    val isLocationRestricted: Boolean = true,

    @SerialName("is_shift_based")
    val isShiftBased: Boolean = false
)
