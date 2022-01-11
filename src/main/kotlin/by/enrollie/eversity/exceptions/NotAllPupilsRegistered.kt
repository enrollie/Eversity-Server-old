/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.exceptions

import by.enrollie.eversity.data_classes.Pupil

class NotAllPupilsRegistered(val notRegisteredPupils: List<Pupil>) : Exception()
