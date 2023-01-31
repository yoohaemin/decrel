/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

/**
 * Welcome to the source code/javadoc of decrel, library for declarative programming with relations.
 *
 * decrel is built on the idea to allow clear expression of relations that are implied in your domain models.
 *
 * Traditionally, one way such relations are frequently expressed are in the form of SQL foreign key constraints.
 * Other places may also include documentation living outside of the codebase as diagrams.
 *
 * All of these expressions of relations are not easily usable directly in the code. Therefore, it is the programmers'
 * job to understand these relations and translate them into imperative steps in order to express a desirable outcome.
 *
 * The goal of this library is to allow expressing underused implicit knowledge as an explicit first-class value,
 * and achieve better expressiveness in other parts of the code as a result.
 */
@scala.annotation.nowarn("msg=package object inheritance")
object `package` extends syntax
