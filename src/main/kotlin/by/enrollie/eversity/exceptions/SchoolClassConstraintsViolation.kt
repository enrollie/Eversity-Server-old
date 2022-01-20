/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.exceptions

import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.UserID

class SchoolClassConstraintsViolation(val violatedConstraints: Pair<ClassID, UserID>) : Exception() {
    override val message: String
        get() = "School class constraints violated: user with ID ${violatedConstraints.second} does not belong to class with ID ${violatedConstraints.first}"
}
