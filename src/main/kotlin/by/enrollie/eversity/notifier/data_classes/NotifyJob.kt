/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.notifier.data_classes

import by.enrollie.eversity.data_classes.Pupil

data class NotifyJob(val pupil: Pupil, val date:String,val lessonsList: List<Short>)
