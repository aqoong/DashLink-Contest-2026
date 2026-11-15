package ai.pleos.contest.dashlink

/** Only a pending request is loading; an unavailable or not-yet-requested value is not. */
internal fun isVehicleGroupLoading(group: String, values: List<VehicleReading>, connecting: Boolean): Boolean =
    values.any { it.status == ReadStatus.LOADING } ||
        (values.isEmpty() && connecting && group != "location")
