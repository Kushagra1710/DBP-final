package com.dbpsecurity.dbpcheckin.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile

object SessionManager {
    var currentUserProfile: Profile? by mutableStateOf(null)
    var currentUserGroup: Group? by mutableStateOf(null)
    var currentReferenceEmbedding: FloatArray? = null // Not a state, just data

    fun clear() {
        currentUserProfile = null
        currentUserGroup = null
        currentReferenceEmbedding = null
    }
}
