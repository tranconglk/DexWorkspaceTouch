package com.trancong.dexworkspacetouch.feature.car

class CarWorkflow(
    val id: String,
    actions: List<CarAction>,
) {
    val actions: List<CarAction> = actions.toList()

    init {
        require(id.isNotBlank()) { "Car workflow ID must not be blank." }
    }
}
