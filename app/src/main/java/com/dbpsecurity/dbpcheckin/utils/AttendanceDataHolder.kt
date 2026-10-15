package com.dbpsecurity.dbpcheckin.utils

import com.dbpsecurity.dbpcheckin.models.Attendance
import com.dbpsecurity.dbpcheckin.models.Group
import com.dbpsecurity.dbpcheckin.models.Profile

object AttendanceDataHolder {
    var lastCheckInTime: String? = null
    var lastCheckOutTime: String? = null
    var currentAttendance: Attendance? = null
    var currentProfile: Profile? = null
    var currentGroup: Group? = null

    fun clear() {
        lastCheckInTime = null
        lastCheckOutTime = null
        currentAttendance = null
        currentProfile = null
        currentGroup = null
    }
}
