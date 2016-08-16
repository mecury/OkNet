/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.mecury.okhttplibrary.internal.tls;

import com.mecury.okhttplibrary.internal.platform.Platform;

import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.X509TrustManager;

/**
 * Computes the effective certificate(证书) chain from the raw array returned by Java's built in TLS APIs.
 * Cleaning a chain returns a list of certificates where the first element is {@code chain[0]}, each
 * certificate is signed by the certificate that follows, and the last certificate is a trusted CA
 * certificate.
 * 计算有效的证书链重java TLS APIS 返回的原始数组。清理一条连接返回这条证书链的第一个元素，每个证书都会被跟在后面
 * 的证书签署，最后一个证书是一个手信任的 CA 证书
 *
 * <p>Use of the chain cleaner is necessary to omit unexpected certificates that aren't relevant to
 * the TLS handshake and to extract the trusted CA certificate for the benefit of certificate
 * pinning.
 */
public abstract class CertificateChainCleaner {

  public abstract List<Certificate> clean(List<Certificate> chain, String hostname)
      throws SSLPeerUnverifiedException;

  public static CertificateChainCleaner get(X509TrustManager trustManager) {
    return Platform.get().buildCertificateChainCleaner(trustManager);
  }

  public static CertificateChainCleaner get(X509Certificate... caCerts) {
    return new BasicCertificateChainCleaner(TrustRootIndex.get(caCerts));
  }
}
