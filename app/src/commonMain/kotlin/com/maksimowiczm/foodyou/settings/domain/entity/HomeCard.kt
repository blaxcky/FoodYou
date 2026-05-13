package com.maksimowiczm.foodyou.settings.domain.entity

enum class HomeCard {
    Calendar,
    Goals,
    Meals,
    Activities;

    companion object {
        val defaultOrder: List<HomeCard> = listOf(Calendar, Goals, Meals, Activities)
    }
}
