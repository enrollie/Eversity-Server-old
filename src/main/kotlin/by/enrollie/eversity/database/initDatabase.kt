/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.database

import by.enrollie.eversity.database.xodus_definitions.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.XdModel
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.util.initMetaData
import java.io.File

fun initXodusDatabase(storeDir: File): TransientEntityStore {
    val models = arrayOf(
        XodusAbsence,
        XodusAbsenceReason,
        XodusAbsenceNotes,
        XodusAbsenceNoteType,
        XodusAdministrationProfile,
        XodusAppData,
        XodusBaseUserProfile,
        XodusClass,
        XodusClassTimetable,
        XodusParentProfile,
        XodusPersistentSchoolClassRoleType,
        XodusPersistentSchoolClassRole,
        XodusPupilProfile,
        XodusSchoolsBy,
        XodusTeacherProfile,
        XodusIntegration,
        XodusToken,
        XodusUser,
        XodusUserType
    )

    XdModel.registerNodes(*models)

    val store = StaticStoreContainer.init(storeDir, "eversity")

    initMetaData(XdModel.hierarchy, store)
    return store
}
