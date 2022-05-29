package com.pupptmstr.scooternav.models

data class RequestPathWebsocketMessage(
    val nodeIdFrom: Long,
    val nodeIdTo: Long
) : WebsocketMessage
