package com.pupptmstr.scooternav.models


data class Way(
    var nodeStart: Node,
    var nodeEnd: Node,

    var id: String = "0",
    var averageSpeed: Double = 10.0, //km per hour
    var bandwidth: Int = 5, //num of client can be that street same time
    var highway: String,
    var surface: String,
    var length: Double
) {
    constructor() : this(
        Node(),
        Node(), "0", 10.0, 5, "no", "no", 0.0)
}
