/*
 * Copyright (c) 2021.
 * Author: Pavel Matusevich.
 * Licensed under GNU AGPLv3.
 * All rights are reserved.
 */

package by.enrollie.eversity.data_classes

/**
 * Declensions of school's (where instance of Eversity is running) name.
 * All fields are commented in Russian
 */
data class SchoolNameDeclensions(
    /**
     * Именительный падеж
     */
    val nominative: String,
    /**
     * Родительный падеж
     */
    val genitive: String,
    /**
     * Дательный падеж
     */
    val dative: String,
    /**
     * Винительный падеж
     */
    val accusative: String,
    /**
     * Творительный падеж
     */
    val instrumental: String,
    /**
     * Предложный падеж
     */
    val prepositional: String,
    /**
     * Место (где?)
     */
    val location:String
)
