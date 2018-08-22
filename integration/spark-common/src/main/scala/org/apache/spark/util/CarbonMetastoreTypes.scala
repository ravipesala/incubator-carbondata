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

package org.apache.spark.util

import java.util
import java.util.{ArrayList, List}

import scala.util.parsing.combinator.RegexParsers

import org.apache.spark.sql.types._
import org.apache.spark.sql.util.CarbonException

import org.apache.carbondata.core.metadata.datatype
import org.apache.carbondata.core.metadata.schema.table.CarbonTable
import org.apache.carbondata.core.metadata.schema.table.column.ColumnSchema
import org.apache.carbondata.spark.util.CarbonScalaUtil

object CarbonMetastoreTypes extends RegexParsers {
  protected lazy val primitiveType: Parser[DataType] =
    "string" ^^^ StringType |
    "varchar" ^^^ StringType |
    "float" ^^^ FloatType |
    "int" ^^^ IntegerType |
    "tinyint" ^^^ ShortType |
    "short" ^^^ ShortType |
    "double" ^^^ DoubleType |
    "long" ^^^ LongType |
    "binary" ^^^ BinaryType |
    "boolean" ^^^ BooleanType |
    fixedDecimalType |
    "decimal" ^^^ "decimal" ^^^ DecimalType(10, 0) |
    "varchar\\((\\d+)\\)".r ^^^ StringType |
    "date" ^^^ DateType |
    "timestamp" ^^^ TimestampType

  protected lazy val fixedDecimalType: Parser[DataType] =
    "decimal" ~> "(" ~> "^[1-9]\\d*".r ~ ("," ~> "^[0-9]\\d*".r <~ ")") ^^ {
      case precision ~ scale =>
        DecimalType(precision.toInt, scale.toInt)
    }

  protected lazy val arrayType: Parser[DataType] =
    "array" ~> "<" ~> dataType <~ ">" ^^ {
      case tpe => ArrayType(tpe)
    }

  protected lazy val mapType: Parser[DataType] =
    "map" ~> "<" ~> dataType ~ "," ~ dataType <~ ">" ^^ {
      case t1 ~ _ ~ t2 => MapType(t1, t2)
    }

  protected lazy val structField: Parser[StructField] =
    "[a-zA-Z0-9_]*".r ~ ":" ~ dataType ^^ {
      case name ~ _ ~ tpe => StructField(name, tpe, nullable = true)
    }

  protected lazy val structType: Parser[DataType] =
    "struct" ~> "<" ~> repsep(structField, ",") <~ ">" ^^ {
      case fields => StructType(fields)
    }

  protected lazy val dataType: Parser[DataType] =
    arrayType |
    mapType |
    structType |
    primitiveType

  def toDataType(metastoreType: String): DataType = {
    parseAll(dataType, metastoreType) match {
      case Success(result, _) => result
      case _: NoSuccess =>
        CarbonException.analysisException(s"Unsupported dataType: $metastoreType")
    }
  }

  def toMetastoreType(dt: DataType): String = {
    dt match {
      case ArrayType(elementType, _) => s"array<${ toMetastoreType(elementType) }>"
      case StructType(fields) =>
        s"struct<${
          fields.map(f => s"${ f.name }:${ toMetastoreType(f.dataType) }")
            .mkString(",")
        }>"
      case StringType => "string"
      case FloatType => "float"
      case IntegerType => "int"
      case ShortType => "tinyint"
      case DoubleType => "double"
      case LongType => "bigint"
      case BinaryType => "binary"
      case BooleanType => "boolean"
      case DecimalType() => "decimal"
      case TimestampType => "timestamp"
      case DateType => "date"
    }
  }

  def convertToSparkSchema(table: CarbonTable, carbonColumns: Array[ColumnSchema]): StructType = {
    val fields: util.List[StructField] = new util.ArrayList[StructField](carbonColumns.length)
    var i: Int = 0
    while ( { i < carbonColumns.length }) {
      val carbonColumn: ColumnSchema = carbonColumns(i)
      val dataType: datatype.DataType = carbonColumn.getDataType
      if (org.apache.carbondata.core.metadata.datatype.DataTypes.isDecimal(dataType)) fields
        .add(new StructField(carbonColumn.getColumnName,
          new DecimalType(carbonColumn.getPrecision, carbonColumn.getScale),
          true,
          Metadata.empty))
      else if (org.apache.carbondata.core.metadata.datatype.DataTypes.isStructType(dataType)) fields
        .add(new StructField(carbonColumn.getColumnName,
          CarbonMetastoreTypes
            .toDataType(String
              .format("struct<%s>",
                SparkTypeConverter.getStructChildren(table, carbonColumn.getColumnName))),
          true,
          Metadata.empty))
      else if (org.apache.carbondata.core.metadata.datatype.DataTypes.isArrayType(dataType)) fields
        .add(new StructField(carbonColumn.getColumnName,
          CarbonMetastoreTypes
            .toDataType(String
              .format("array<%s>",
                SparkTypeConverter.getArrayChildren(table, carbonColumn.getColumnName))),
          true,
          Metadata.empty))
      else fields
        .add(new StructField(carbonColumn.getColumnName,
          CarbonScalaUtil.convertCarbonToSparkDataType (carbonColumn.getDataType),
          true,
          Metadata.empty))

      { i += 1; i - 1 }
    }
    new StructType(fields.toArray(new Array[StructField](0)))
  }

}
