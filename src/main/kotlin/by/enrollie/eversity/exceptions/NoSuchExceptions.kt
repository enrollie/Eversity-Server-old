/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.exceptions

import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.UserID

class NoSuchSchoolClassException(val classID: ClassID) : Exception("No class with ID $classID found")

class NoSuchUserException(val userID: UserID) : Exception("No user with ID $userID found")
