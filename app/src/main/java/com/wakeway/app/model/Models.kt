package com.wakeway.app.model

enum class TransportMode(val label: String, val emoji: String) {
    TRAIN("Train", "🚆"),
    METRO("Metro", "🚇"),
    BUS("Bus", "🚌"),
    TAXI("Taxi", "🚕"),
    AUTO("Auto", "🛺"),
    CAR("Car", "🚗"),
    WALK("Walking", "🚶"),
    BIKE("Bike", "🚲")
}

enum class JourneyStatus {
    ACTIVE, PAUSED, COMPLETED, MISSED, CANCELLED
}

enum class AlertTrigger {
    DISTANCE, TIME, DESTINATION
}

data class Destination(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class JourneyAlert(
    val trigger: AlertTrigger,
    val value: Double,
    val label: String
)

data class Journey(
    val id: String,
    val destination: Destination,
    val transport: TransportMode,
    val alerts: List<JourneyAlert>,
    val startedAt: Long,
    val status: JourneyStatus = JourneyStatus.ACTIVE,
    val acknowledged: Boolean = false
)

data class LocationState(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMps: Float
)
