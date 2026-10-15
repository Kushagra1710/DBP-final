package com.dbpsecurity.dbpcheckin.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String? = null,

    @SerialName("email")
    val email: String? = null,

    @SerialName("phone")
    val phone: String? = null,

    @SerialName("tehsil")
    val seating: String? = null,

    @SerialName("image_url")
    val imageUrl: String? = null,

    @SerialName("role")
    val role: String = "employee",

    @SerialName("group_id")
    val groupId: String? = null,

    @SerialName("requested_group_id")
    val requestedGroupId: String? = null,

    @SerialName("request_status")
    val requestStatus: String? = "none", // 'none', 'pending', 'approved', 'rejected'

    @SerialName("latitude")
    val latitude: Double? = null,

    @SerialName("longitude")
    val longitude: Double? = null,

    @SerialName("radius")
    val radius: Double? = 100.0
)
