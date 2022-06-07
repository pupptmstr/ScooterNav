package com.pupptmstr.scooternav.models

data class DataWebSocketMessage(
    val speed: Double,
    val node1: Coordinates,
    val node2: Coordinates
)
