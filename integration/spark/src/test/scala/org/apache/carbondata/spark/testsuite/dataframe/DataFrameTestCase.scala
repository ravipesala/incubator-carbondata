/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.carbondata.spark.testsuite.dataframe

import java.io.File

import org.apache.spark.sql.{DataFrame, Row, SaveMode}
import org.apache.spark.sql.common.util.CarbonHiveContext._
import org.apache.spark.sql.common.util.{CarbonHiveContext, QueryTest}
import org.scalatest.BeforeAndAfterAll

/**
 * Test Class for hadoop fs relation
 *
 */
class DataFrameTestCase extends QueryTest with BeforeAndAfterAll {

  test("json data with long datatype issue CARBONDATA-405") {
    val currentDirectory = new File(this.getClass.getResource("/").getPath + "/../../")
      .getCanonicalPath
    val jsonDF = read.format("json").load("./src/test/resources/test.json")
    jsonDF.write
      .format("carbondata")
      .option("tableName", "dftesttable")
      .option("compress", "true")
      .mode(SaveMode.Overwrite)
      .save()
    val carbonDF = read
      .format("carbondata")
      .option("tableName", "dftesttable")
      .load()
    checkAnswer(
      carbonDF.select("age", "name"),
      jsonDF.select("age", "name"))
  }

  override def afterAll {
    sql("drop table dftesttable")
  }
}