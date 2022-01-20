/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_functions

import org.joda.time.format.DateTimeFormatter
import java.text.ParseException
import java.text.SimpleDateFormat

fun <T> Pair<Array<T>, Array<T>>.join() = this.first + this.second

val <A, B> Pair<A?, B?>.areNulls: Boolean
    get() = this.first == null || this.second == null

fun SimpleDateFormat.tryToParse(string: String) = try {
    this.parse(string)
} catch (e: ParseException) {
    null
}

fun DateTimeFormatter.tryToParse(string: String) = try {
    this.parseDateTime(string)
} catch (e: IllegalArgumentException) {
    null
}
