/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.exceptions

class SchoolsByNotAvailableException:Exception {
    constructor() : super()
    constructor(msg: String) : super(msg)
}