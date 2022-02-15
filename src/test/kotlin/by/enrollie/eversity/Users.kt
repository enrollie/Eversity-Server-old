/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

import by.enrollie.eversity.data_classes.*
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.database.initXodusDatabase
import com.github.ajalt.mordant.rendering.TextColors
import jetbrains.exodus.database.TransientEntityStore
import org.joda.time.DateTime
import org.junit.jupiter.api.DisplayName
import java.nio.file.Files
import kotlin.test.*

class Users {
    companion object {
        var store: TransientEntityStore = initXodusDatabase(Files.createTempDirectory("eversity-test-users").toFile())
        private val pupils =
            arrayOf(
                Pupil(4, "Егор", null, "Авгейчик", 123),
                Pupil(1, "Павел", "Андреевич", "Матусевич", 123),
                Pupil(2, "Андрей", null, "Хайзенберг", 123),
                Pupil(3, "Евгений", null, "Хайзенберг", 123),
                Pupil(5, "Сергей", null, "Чех", 123),
                Pupil(1223, "Ярослав", "Николаевич", "Бобиков", 125)
            )
        val teacher = Administration(0, "Татьяна", "Михайловна", "Ширяева")
    }

    init {
        registerTeacher(
            teacher.id,
            UserName(teacher.firstName, teacher.middleName, teacher.lastName),
            null,
            TwoShiftsTimetable(),
            true, store
        )
        registerClass(123, teacher.id, "12 \"Б\"", false, Timetable(), store)
        registerClass(125, teacher.id, "4 \"Б\"", true, Timetable(), store)
        registerManyPupils(pupils, store)
    }

    @Test
    fun getPupilsInClassWithClassID() {
        val result = getPupilsInClass(123, store) + getPupilsInClass(125, store)
        assert(result.isNotEmpty())
        assertContentEquals(pupils, result)
    }

    @Test
    fun checkSorted() {
        val result = getPupilsInClass(123, store)
        assertContentEquals(result,
            pupils.filter { it.classID == 123 }.sortedBy { "${it.lastName} ${it.firstName}" }.toTypedArray())
    }

    @Test
    fun nonExistingClassFails() {
        assertFailsWith<NoSuchElementException> {
            getPupilsInClass(1, store)
        }
    }

    @Test
    fun testClassIDFromPupil() {
        assert(getPupilClass(2, store).id == 123)
    }

    @Test
    fun testGettingUserNameByID() {
        for (pupil in pupils) {
            assertEquals(getUserName(pupil.id, store), UserName(pupil.firstName, pupil.middleName, pupil.lastName))
        }
        assert(getUserName(0, store) == UserName("Татьяна", "Михайловна", "Ширяева"))
    }

    @Test
    fun getAllUsersInDB() {
        val result = getAllUsers(store)
        assert(result.containsAll(pupils.toList()))
        assertContains(result, teacher)
    }

    @Test
    fun checkNonExistentPupils() {
        val pupil = Pupil(0, "Евгений", "Васильевич", "Базаров", 0)
        val result = getNonExistentPupilsIDs(pupils.plus(pupil).toList(), store)
        assertContains(result, pupil)
        assert(result.size == 1)
    }

    @Test
    fun checkPupilsCount() {
        val result = getPupilsCount(DateTime.now(), store)
        println(result)
        assert(result.first == pupils.size - 1)
        assert(result.second == 1)
    }

    @Test
    @DisplayName("Check if deleting a user changes something")
    fun checkDeleting() {
        disableUser(2, store)
        assert(getAllUsers(store).size == pupils.size)
        val data = getPupilsCount(DateTime.now().plusDays(1), store)
        assert(data.first == pupils.size - 2)
        assert(data.second == 1)
        enableUser(2, store) // Revert this test
    }
}
