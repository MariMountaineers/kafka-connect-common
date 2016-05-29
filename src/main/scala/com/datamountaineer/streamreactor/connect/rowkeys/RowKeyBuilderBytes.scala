package com.datamountaineer.streamreactor.connect.rowkeys

import com.datamountaineer.streamreactor.connect.schemas.BytesHelper._
import com.datamountaineer.streamreactor.connect.config.Field
import org.apache.avro.generic.GenericRecord
import org.apache.hadoop.hbase.util.Bytes
import org.apache.kafka.connect.data.{Schema, Struct}
import org.apache.kafka.connect.sink.SinkRecord

import scala.collection.JavaConversions._

/**
  * Builds a row key for the given connect record and payload. The implementations would decide what is using and how.
  */
trait RowKeyBuilderBytes {
  def build(record: SinkRecord, payload: Any): Array[Byte]
}

/**
  * Uses the connect record (topic, partition, offset) to set the schema
  *
  * @param keyDelimiter Delimiter to use in keys
  */
class GenericRowKeyBuilderBytes(keyDelimiter: String = "|") extends RowKeyBuilderBytes {
  val delimiterBytes = Bytes.toBytes(keyDelimiter)

  override def build(record: SinkRecord, payload: Any): Array[Byte] = {
    Bytes.add(
      Array(
        Bytes.toBytes(record.topic()),
        delimiterBytes,
        Bytes.toBytes(record.kafkaPartition().toString),
        delimiterBytes,
        Bytes.toBytes(record.kafkaOffset().toString)
      )
    )
  }
}

/**
  * Creates a row key based on the connect SinkRecord instance key. Only connect Schema primitive types are handled
  */
class SinkRecordKeyRowKeyBuilderBytes extends RowKeyBuilderBytes {
  override def build(record: SinkRecord, payload: Any): Array[Byte] = {
    val `type` = record.keySchema().`type`()
    require(`type`.isPrimitive, "The SinkRecord key schema is not a primitive type")

    `type`.name() match {
      case "INT8" => record.key.fromByte()
      case "INT16" => record.key.fromShort()
      case "INT32" => record.key.fromInt()
      case "INT64" => record.key.fromLong()
      case "FLOAT32" => record.key.fromFloat()
      case "FLOAT64" => record.key.fromDouble()
      case "BOOLEAN" => record.key.fromBoolean()
      case "STRING" => record.key.fromString()
      case "BYTES" => record.key().fromBytes()
      case other => throw new IllegalArgumentException(s"$other is not supported by the ${getClass.getName}")
    }
  }
}


/**
  * Uses the fields present in the avro record to build the key. More than one key can be used.
  *
  * @param valuesExtractorsMap A map of functions converting the avro field value to bytes
  * @param keys                - Which avro record fields make up the schema
  * @param keyDelimiter       Delimiter to use in the key
  */
class AvroRecordRowKeyBuilderBytes(valuesExtractorsMap: Map[String, (Any) => Array[Byte]],
                                   keys: Seq[String],
                                   keyDelimiter: String = "\n") extends RowKeyBuilderBytes {

  require(keys.nonEmpty, "Invalid keys provided.")
  require(keyDelimiter != null, "Invalid composite row  key delimiter specified.")
  val delimBytes = Bytes.toBytes(keyDelimiter)

  /**
    * Builds the Hbase row key from the given avro record.
    *
    * @param record  - Instance of the connect record
    * @param payload - Instance of an avro record
    * @return A byte array representing the key to use when inserting the new data into Hbase
    */
  override def build(record: SinkRecord, payload: Any): Array[Byte] = {
    val avroRecord = payload.asInstanceOf[GenericRecord]
    val key = keys.map { case k =>
      val fn = valuesExtractorsMap.getOrElse(k, throw new IllegalArgumentException(s"Could not handle key builder for field:$k"))
      fn(avroRecord.get(k))
    }.flatMap(arr => Seq(delimBytes, arr))
    Bytes.add(key.tail.toArray)
  }
}

case class StructFieldsRowKeyBuilderBytes(fm : List[Field],
                                          keyDelimiter: String = "\n") extends RowKeyBuilderBytes {
  require(fm.nonEmpty, "Invalid keys provided")

  val delimBytes = Bytes.toBytes(keyDelimiter)

  override def build(record: SinkRecord, payload: Any): Array[Byte] = {
    val struct = record.value().asInstanceOf[Struct]
    val schema = struct.schema

    val availableFields: Set[String] = schema.fields().map(_.name).toSet
    val missingKeys = fm.filterNot(p => availableFields.contains(p.name))
    require(missingKeys.isEmpty, s"${missingKeys.mkString(",")} keys are not present in the SinkRecord payload:${availableFields.mkString(",")}")

    val keyBytes = fm.map { case key =>
      val field = schema.field(key.name)
      val value = struct.get(field)
      require(value != null, s"$key field value is null. Non null value is required for the fileds creating the row key")
      field.schema() match {
        case Schema.BOOLEAN_SCHEMA | Schema.OPTIONAL_BOOLEAN_SCHEMA => value.fromBoolean()
        case Schema.BYTES_SCHEMA | Schema.OPTIONAL_BYTES_SCHEMA => value.fromBytes()
        case Schema.FLOAT32_SCHEMA | Schema.OPTIONAL_FLOAT32_SCHEMA => value.fromFloat()
        case Schema.FLOAT64_SCHEMA | Schema.OPTIONAL_FLOAT64_SCHEMA => value.fromDouble()
        case Schema.INT8_SCHEMA | Schema.OPTIONAL_INT8_SCHEMA => value.fromByte()
        case Schema.INT16_SCHEMA | Schema.OPTIONAL_INT16_SCHEMA => value.fromShort()
        case Schema.INT32_SCHEMA | Schema.OPTIONAL_INT32_SCHEMA => value.fromInt()
        case Schema.INT64_SCHEMA | Schema.OPTIONAL_INT64_SCHEMA => value.fromLong()
        case Schema.STRING_SCHEMA | Schema.OPTIONAL_STRING_SCHEMA => value.fromString()
      }
    }.flatMap(arr => Seq(delimBytes, arr))
    Bytes.add(keyBytes.tail.toArray)
  }
}
