package io.github.persiancalendar.gradle

import groovy.json.JsonSlurper
import java.io.File

// TODO: Turn it into a real gradle task maybe
object GenerateAppSrcTask {

    operator fun File.div(child: String) = File(this, child)

    fun generateEventsCode(eventsJson: File, eventsOutput: File) {
        val events = JsonSlurper().parse(eventsJson) as Map<*, *>
        val (persianEvents, islamicEvents, gregorianEvents, nepaliEvents) = listOf(
            "Persian Calendar", "Hijri Calendar", "Gregorian Calendar", "Nepali Calendar"
        ).map { key ->
            (events[key] as List<*>).joinToString(",\n    ") {
                val record = it as Map<*, *>
                "CalendarRecord(title = \"${record["title"]}\"," +
                        " type = EventType.${record["type"]}," +
                        " isHoliday = ${record["holiday"]}," +
                        " month = ${record["month"]}, day = ${record["day"]})"
            }
        }
        val irregularRecurringEvents = (events["Irregular Recurring"] as List<*>)
            .mapNotNull { it as Map<*, *> }
            .joinToString(",\n    ") { event ->
                "mapOf(${event.map { (k, v) -> """"$k" to "$v"""" }.joinToString(", ")})"
            }
        val eventsSource = (events["Source"] as Map<*, *>).toList()
            .joinToString(",\n    ") { (k, v) -> """$k("$v")""" }
        eventsOutput.writeText(
            """package com.byagowi.persiancalendar.generated

enum class EventType(val source: String) {
    $eventsSource
}

class CalendarRecord(val title: String, val type: EventType, val isHoliday: Boolean, val month: Int, val day: Int)

val persianEvents = listOf<CalendarRecord>(
    $persianEvents
)

val islamicEvents = listOf<CalendarRecord>(
    $islamicEvents
)

val gregorianEvents = listOf<CalendarRecord>(
    $gregorianEvents
)

val nepaliEvents = listOf<CalendarRecord>(
    $nepaliEvents
)

val irregularRecurringEvents = listOf(
    $irregularRecurringEvents
)
"""
        )
    }

    fun generateCitiesCode(citiesJson: File, citiesOutput: File) {
        val cities = (JsonSlurper().parse(citiesJson) as Map<*, *>).flatMap { countryEntry ->
            val countryCode = countryEntry.key as String
            val country = countryEntry.value as Map<*, *>
            (country["cities"] as Map<*, *>).map { cityEntry ->
                val key = cityEntry.key as String
                val city = cityEntry.value as Map<*, *>
                val latitude = (city["latitude"] as Number).toDouble()
                val longitude = (city["longitude"] as Number).toDouble()
                // Elevation really degrades quality of calculations
                val elevation =
                    if (countryCode == "ir") .0 else (city["elevation"] as Number).toDouble()
                """"$key" to CityItem(
            key = "$key",
            en = "${city["en"]}", fa = "${city["fa"]}",
            ckb = "${city["ckb"]}", ar = "${city["ar"]}",
            countryCode = "$countryCode",
            countryEn = "${country["en"]}", countryFa = "${country["fa"]}",
            countryCkb = "${country["ckb"]}", countryAr = "${country["ar"]}",
            coordinates = Coordinates($latitude, $longitude, $elevation)
        )"""
            }
        }.joinToString(",\n    ")
        citiesOutput.writeText(
            """package com.byagowi.persiancalendar.generated

import com.byagowi.persiancalendar.entities.CityItem
import io.github.persiancalendar.praytimes.Coordinates

val citiesStore = mapOf(
    $cities
)
    """
        )
    }

    fun generateDistrictsCode(districtsJson: File, districtsOutput: File) {
        val districts =
            (JsonSlurper().parse(districtsJson) as Map<*, *>).mapNotNull { province ->
                val provinceName = province.key as String
                if (provinceName.startsWith("#")) return@mapNotNull null
                "\"$provinceName\" to listOf(\n" + (province.value as Map<*, *>).map { county ->
                    val key = county.key as String
                    """       "$key;""" + (county.value as Map<*, *>).map { district ->
                        val coordinates = district.value as Map<*, *>
                        val latitude = (coordinates["lat"] as Number).toDouble()
                        val longitude = (coordinates["long"] as Number).toDouble()
                        // Remove what is in the parenthesis
                        val name = district.key.toString().split("(")[0]
                        "$name:$latitude:$longitude"
                    }.joinToString(";") + "\""
                }.joinToString(",\n") + "\n    )"
            }.joinToString(",\n    ")
        districtsOutput.writeText(
            """package com.byagowi.persiancalendar.generated

import com.byagowi.persiancalendar.entities.CityItem
import io.github.persiancalendar.praytimes.Coordinates

val distrcitsStore = listOf(
    $districts
)
"""
        )
    }
}
