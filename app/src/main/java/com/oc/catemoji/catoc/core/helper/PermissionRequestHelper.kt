package com.oc.catemoji.catoc.core.helper

class PermissionRequestHelper {
    private var requestCount = 0

    companion object { const val MAX_REQUEST = 2 }

    fun onDenied() { requestCount++ }
    fun onGranted() { requestCount = 0 }
    fun shouldGoToSettings() = requestCount >= MAX_REQUEST
    fun reset() { requestCount = 0 }
}