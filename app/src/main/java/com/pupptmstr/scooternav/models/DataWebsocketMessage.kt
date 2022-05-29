package com.pupptmstr.scooternav.models

data class DataWebsocketMessage(
    val averageSpeed: Double,
    val wayId: Long
): WebsocketMessage
