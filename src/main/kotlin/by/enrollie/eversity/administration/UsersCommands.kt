/*
 * Copyright © 2021 - 2022.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.administration

import by.enrollie.eversity.CLI
import by.enrollie.eversity.data_classes.ClassID
import by.enrollie.eversity.data_classes.User
import by.enrollie.eversity.data_classes.UserType
import by.enrollie.eversity.database.functions.*
import by.enrollie.eversity.security.EversityJWT
import by.enrollie.eversity.serializers.UsersJSON
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType.getInteger
import com.mojang.brigadier.arguments.IntegerArgumentType.integer
import com.mojang.brigadier.arguments.StringArgumentType.getString
import com.mojang.brigadier.arguments.StringArgumentType.string
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder.literal
import com.mojang.brigadier.builder.RequiredArgumentBuilder.argument
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class UserDataEdit(
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val userType: UserType? = null,
    val classID: ClassID? = null,
)

private fun User.copy(
    firstName: String? = null,
    middleName: String? = null,
    lastName: String? = null,
    userType: UserType? = null,
): User {
    return object : User {
        override val id: Int = this@copy.id
        override val type: UserType = userType ?: this@copy.type
        override val firstName: String = firstName ?: this@copy.firstName
        override val middleName: String? = middleName ?: this@copy.middleName
        override val lastName: String = lastName ?: this@copy.lastName
    }
}

private fun validateUserTypeTransition(oldType: UserType, newType: UserType) {
    if ((oldType == UserType.Administration || oldType == UserType.Teacher || oldType == UserType.Social) && (newType != UserType.Administration && newType != UserType.Teacher && newType != UserType.Social))
        throw IllegalArgumentException("User types $oldType and $newType are not compatible")
}

internal fun CommandDispatcher<String>.registerUserCommands() {
    val getUserCommand: LiteralArgumentBuilder<String> =
        literal<String>("getUser").then(argument<String?, Int?>("id", integer()).suggests { _, builder ->
            builder.apply {
                getAllUsers().forEach {
                    suggest(it.id)
                }
            }.buildFuture()
        }.executes { context ->
            val id = getInteger(context, "id")
            printResponse(UsersJSON.encodeToString(getUserInfo(id)))
            return@executes 0
        })
    val listUsersCommand: LiteralArgumentBuilder<String> =
        literal<String?>("listUsers").then(argument<String?, String?>("filterType", string()).executes { context ->
            val type = when (getString(context, "filterType")) {
                "pupil" -> UserType.Pupil
                "parent" -> UserType.Parent
                "administrator", "administration" -> UserType.Administration
                "teacher" -> UserType.Teacher
                "system" -> UserType.SYSTEM
                "social" -> UserType.Social
                else -> error("Unknown type")
            }
            printResponse(UsersJSON.encodeToString(getAllUsers().filter { it.type == type }))
            return@executes 0
        }).executes {
            printResponse(UsersJSON.encodeToString(getAllUsers()))
            return@executes 0
        }
    val setUser: LiteralArgumentBuilder<String> =
        literal<String?>("setUser").then(argument<String?, Int?>("userID", integer()).then(
            argument<String?, String?>("edits", string()).executes { context ->
                val userID = getInteger(context, "userID")
                val edits = Json.decodeFromString<UserDataEdit>(getString(context, "edits"))
                val newUser = (getUserInfo(userID) ?: error("No user with ID $userID was found")).apply {
                    if (edits.userType != null) validateUserTypeTransition(type,
                        edits.userType)
                }.copy(edits.firstName,
                    edits.middleName,
                    edits.lastName,
                    edits.userType)
                CLI.lineReader.printAbove("Warning! This action can potentially lead to data inconsistency! Continue?")
                val acceptance = CLI.lineReader.readLine("[y/N]: ")
                if (acceptance.lowercase() == "y")
                    applyUserDataEdits(newUser)
                else return@executes 0
                return@executes 0
            }))
    val deleteUser =
        literal<String>("deleteUser").then(argument<String?, Int?>("userID", integer()).executes { context ->
            val userID = getInteger(context, "userID")
            val user = getUserInfo(userID) ?: error("Requested user was not found")
            CLI.lineReader.printAbove(TextColors.red("WARNING!") + " This action will delete next user: \'${
                UsersJSON.encodeToString(user)
            }\', which can lead to ${TextStyles.bold("horrible")} results, such as mass data deletion.")
            val acceptance = CLI.lineReader.readLine("Continue? [y/N]: ")
            if (acceptance.lowercase() == "y") {
                CLI.logger.info("CLI user requested and confirmed deletion of user with ID $userID (JSON: \'${
                    UsersJSON.encodeToString(user)
                }\'). Executing...")
                disableUser(userID)
            }

            return@executes 0
        })
    val issueTokenCommand = literal<String>("issueToken").then(
        argument<String?, Int?>("userId", integer()).suggests { _, builder ->
            builder.apply {
                getAllUsers().forEach {
                    suggest(it.id)
                }
            }.buildFuture()
        }.executes { context ->
            val userID = getInteger(context, "userId")
            val token = issueToken(userID)
            printResponse(EversityJWT.instance.sign(userID.toString(), token))
            0
        }
    )
    register(setUser)
    register(getUserCommand)
    register(listUsersCommand)
    register(deleteUser)
    register(issueTokenCommand)
}
