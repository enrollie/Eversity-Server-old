/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.exceptions

fun noClassError(classID: Int, message: String = "Class with ID $classID was not found"): Nothing =
    throw NoSuchElementException(message)

fun noUserError(userID: Int, message: String = "User with ID $userID was not found"): Nothing =
    throw NoSuchElementException(message)
