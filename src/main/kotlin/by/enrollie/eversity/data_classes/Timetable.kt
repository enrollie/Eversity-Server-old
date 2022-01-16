/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.DayOfWeek

internal fun Pair<Array<com.neitex.Lesson>, Array<com.neitex.Lesson>>.toLocalPair(): Pair<Array<Lesson>, Array<Lesson>> =
    Pair(this.first.toLessons(), this.second.toLessons())

@Serializable
class Timetable {
    private var lessons: Map<DayOfWeek, Array<Lesson>>

    constructor(lessonsMap: Map<DayOfWeek, Array<Lesson>>) {
        require(lessonsMap.keys.containsAll(kotlin.run {
            val set = mutableSetOf<DayOfWeek>()
            DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }.toCollection(set)
            return@run set
        }))
        lessons = lessonsMap
    }

    constructor(
        monday: Array<Lesson> = arrayOf(),
        tuesday: Array<Lesson> = arrayOf(),
        wednesday: Array<Lesson> = arrayOf(),
        thursday: Array<Lesson> = arrayOf(),
        friday: Array<Lesson> = arrayOf(),
        saturday: Array<Lesson> = arrayOf()
    ) {
        lessons = kotlin.run {
            val map = mutableMapOf<DayOfWeek, Array<Lesson>>()
            map[DayOfWeek.MONDAY] = monday
            map[DayOfWeek.TUESDAY] = tuesday
            map[DayOfWeek.WEDNESDAY] = wednesday
            map[DayOfWeek.THURSDAY] = thursday
            map[DayOfWeek.FRIDAY] = friday
            map[DayOfWeek.SATURDAY] = saturday
            map.toMap()
        }
    }

    constructor(timetable: com.neitex.Timetable) {
        lessons = kotlin.run {
            val map = mutableMapOf<DayOfWeek, Array<Lesson>>()
            map[DayOfWeek.MONDAY] = timetable.monday.toLessons()
            map[DayOfWeek.TUESDAY] = timetable.tuesday.toLessons()
            map[DayOfWeek.WEDNESDAY] = timetable.wednesday.toLessons()
            map[DayOfWeek.THURSDAY] = timetable.thursday.toLessons()
            map[DayOfWeek.FRIDAY] = timetable.friday.toLessons()
            map[DayOfWeek.SATURDAY] = timetable.saturday.toLessons()
            map.toMap()
        }
    }

    operator fun get(day: DayOfWeek): Array<Lesson> {
        require(day != DayOfWeek.SUNDAY)
        return lessons[day]!!
    }

    operator fun set(day: DayOfWeek, value: Array<Lesson>) {
        require(day != DayOfWeek.SUNDAY)
        lessons = lessons.minus(day).plus(Pair(day, value))
    }

    val monday: Array<Lesson>
        get() = lessons[DayOfWeek.MONDAY]!!
    val tuesday: Array<Lesson>
        get() = lessons[DayOfWeek.TUESDAY]!!
    val wednesday: Array<Lesson>
        get() = lessons[DayOfWeek.WEDNESDAY]!!
    val thursday: Array<Lesson>
        get() = lessons[DayOfWeek.THURSDAY]!!
    val friday: Array<Lesson>
        get() = lessons[DayOfWeek.FRIDAY]!!
    val saturday: Array<Lesson>
        get() = lessons[DayOfWeek.SATURDAY]!!
}

@Serializable
class TwoShiftsTimetable {

    private var lessons: Map<DayOfWeek, Pair<Array<Lesson>, Array<Lesson>>>

    constructor(lessonsMap: Map<DayOfWeek, Pair<Array<Lesson>, Array<Lesson>>>) {
        require(lessonsMap.keys.containsAll(kotlin.run {
            val set = mutableSetOf<DayOfWeek>()
            DayOfWeek.values().filter { it != DayOfWeek.SUNDAY }.toCollection(set)
            return@run set
        }))
        lessons = lessonsMap
    }

    constructor(
        monday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf()),
        tuesday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf()),
        wednesday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf()),
        thursday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf()),
        friday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf()),
        saturday: Pair<Array<Lesson>, Array<Lesson>> = Pair(arrayOf(), arrayOf())
    ) {
        lessons = kotlin.run {
            val map = mutableMapOf<DayOfWeek, Pair<Array<Lesson>, Array<Lesson>>>()
            map[DayOfWeek.MONDAY] = monday
            map[DayOfWeek.TUESDAY] = tuesday
            map[DayOfWeek.WEDNESDAY] = wednesday
            map[DayOfWeek.THURSDAY] = thursday
            map[DayOfWeek.FRIDAY] = friday
            map[DayOfWeek.SATURDAY] = saturday
            map.toMap()
        }
    }

    constructor(parserTimetable: com.neitex.TwoShiftsTimetable) {
        lessons = kotlin.run {
            val map = mutableMapOf<DayOfWeek, Pair<Array<Lesson>, Array<Lesson>>>()
            map[DayOfWeek.MONDAY] = parserTimetable.monday.toLocalPair()
            map[DayOfWeek.TUESDAY] = parserTimetable.tuesday.toLocalPair()
            map[DayOfWeek.WEDNESDAY] = parserTimetable.wednesday.toLocalPair()
            map[DayOfWeek.THURSDAY] = parserTimetable.thursday.toLocalPair()
            map[DayOfWeek.FRIDAY] = parserTimetable.friday.toLocalPair()
            map[DayOfWeek.SATURDAY] = parserTimetable.saturday.toLocalPair()
            map.toMap()
        }
    }

    operator fun get(day: DayOfWeek): Pair<Array<Lesson>, Array<Lesson>> =
        if (day == DayOfWeek.SUNDAY) Pair(arrayOf(), arrayOf()) else lessons[day]!!

    operator fun set(day: DayOfWeek, value: Pair<Array<Lesson>, Array<Lesson>>) {
        require(day != DayOfWeek.SUNDAY)
        lessons = lessons.minus(day).plus(Pair(day, value))
    }

    val monday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.MONDAY]!!
    val tuesday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.TUESDAY]!!
    val wednesday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.WEDNESDAY]!!
    val thursday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.THURSDAY]!!
    val friday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.FRIDAY]!!
    val saturday: Pair<Array<Lesson>, Array<Lesson>>
        get() = lessons[DayOfWeek.SATURDAY]!!

    val asJsonElement: JsonElement
        get() = Json.encodeToJsonElement(lessons)
}
