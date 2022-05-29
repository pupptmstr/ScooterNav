package com.pupptmstr.scooternav.models

data class PathQueryResult(
    val path: List<Node>,
    val lengths: Array<Double>,
    val totalLength: Double
)
