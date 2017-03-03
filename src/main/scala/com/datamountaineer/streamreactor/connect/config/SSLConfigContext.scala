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

package com.datamountaineer.streamreactor.connect.config

import java.io.FileInputStream
import java.security.{KeyStore, SecureRandom}
import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext, TrustManagerFactory}

/**
  * Created by andrew@datamountaineer.com on 14/04/16. 
  * stream-reactor
  */
object SSLConfigContext{
  def apply(config: SSLConfig) = {
    getSSLContext(config)
  }

  /**
    * Get a SSL Connect for a given set of credentials
    *
    * @param config An SSLConfig containing key and truststore credentials
    * @return a SSLContext
    * */
  def getSSLContext(config: SSLConfig) = {
    val useClientCertAuth = config.useClientCert

    //is client certification authentication set
    val keyManagers : Array[KeyManager] = useClientCertAuth match {
      case true => getKeyManagers(config)
      case false => Array[KeyManager]()
    }

    val ctx: SSLContext = SSLContext.getInstance("SSL")
    val trustManagers = getTrustManagers(config)
    ctx.init(keyManagers, trustManagers, new SecureRandom())
    ctx
  }

  /**
    * Get an array of Trust Managers
    * @param config An SSLConfig containing key and truststore credentials
    * @return An Array of TrustManagers
    * */
  def getTrustManagers(config: SSLConfig) = {
    val tsf = new FileInputStream(config.trustStorePath)
    val ts = KeyStore.getInstance("JKS")
    ts.load(tsf, config.trustStorePass.toCharArray)
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(ts)
    tmf.getTrustManagers
  }

  /**
    * Get an array of Key Managers
 *
    * @param config An SSLConfig containing key and truststore credentials
    * @return An Array of KeyManagers
    * */
  def getKeyManagers(config: SSLConfig) : Array[KeyManager] = {
    require(config.keyStorePath.nonEmpty, "Key store path is not set!")
    require(config.keyStorePass.nonEmpty, "Key store password is not set!")
    val ksf = new FileInputStream(config.keyStorePath.get)
    val ks = KeyStore.getInstance("JKS")
    ks.load(ksf, config.keyStorePass.get.toCharArray)
    val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(ks, config.keyStorePass.get.toCharArray)
    kmf.getKeyManagers
  }

}

/**
  * Class for holding key and truststore settings
  * */
case class SSLConfig(trustStorePath: String,
                     trustStorePass: String,
                     keyStorePath: Option[String],
                     keyStorePass: Option[String],
                     useClientCert : Boolean = false)
