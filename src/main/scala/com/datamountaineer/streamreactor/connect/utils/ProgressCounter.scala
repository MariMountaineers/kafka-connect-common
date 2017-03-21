/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.utils

import java.text.SimpleDateFormat
import java.util.Date

import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.connect.connector.ConnectRecord

import scala.collection.immutable.Seq
import scala.collection.mutable

/**
  * Created by andrew@datamountaineer.com on 03/03/2017. 
  * kafka-connect-common
  */
case class ProgressCounter(periodMillis: Int) extends StrictLogging {
  private val startTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
  private var timestamp: Long = 0
  private val counter = mutable.Map.empty[String, Long]

  def update[T <: ConnectRecord[T]](records: Seq[ConnectRecord[T]]): Unit = {
    val newTimestamp = System.currentTimeMillis()

    records.foreach(r => counter.put(r.topic() , counter.getOrElse(r.topic(), 0L) + 1L))

    if (counter.nonEmpty && (newTimestamp - timestamp) >= 60000 && records.nonEmpty) {
      counter.foreach( { case (k,v) => logger.info(s"Delivered $v records for $k since $startTime") })
      counter.empty
      timestamp = newTimestamp
    }
  }

}
