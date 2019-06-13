/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.sql.leo.command

import org.apache.spark.sql.{CarbonEnv, Row, SparkSession}
import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.analysis.NoSuchDatabaseException
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.execution.command.{DropDatabaseCommand, RunnableCommand}
import org.apache.spark.sql.execution.command.table.CarbonDropTableCommand

import org.apache.carbondata.core.util.CarbonUtil

case class LeoDropDatabaseCommand(command: DropDatabaseCommand)
  extends RunnableCommand {

  override val output: Seq[Attribute] = command.output

  override def run(sparkSession: SparkSession): Seq[Row] = {

    // delete name space in hbase
    // drop db in carbon


    var rows: Seq[Row] = Seq()
    val dbName = command.databaseName
    var tablesInDB: Seq[TableIdentifier] = null
    if (sparkSession.sessionState.catalog.listDatabases().exists(_.equalsIgnoreCase(dbName))) {
      tablesInDB = sparkSession.sessionState.catalog.listTables(dbName)
    }
    var databaseLocation = ""
    try {
      databaseLocation = CarbonEnv.getDatabaseLocation(dbName, sparkSession)
    } catch {
      case e: NoSuchDatabaseException =>
        // if database not found and ifExists true return empty
        if (command.ifExists) {
          return rows
        }
    }
    // DropHiveDB command will fail if cascade is false and one or more table exists in database
    if (command.cascade && tablesInDB != null) {
      tablesInDB.foreach { tableName =>
        CarbonDropTableCommand(true, tableName.database, tableName.table).run(sparkSession)
      }
    }
    rows = command.run(sparkSession)
    CarbonUtil.dropDatabaseDirectory(databaseLocation)
    rows
  }
}