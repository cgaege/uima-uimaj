/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.uima.impl;
/**
 * The source for this class is located in 
 *   src/main/versionJava/org/apache/uima/impl/UimaVersion.java
 *   
 * It is processed at build time to create a java source, by substituting
 * values from the build into some fields.
 *   The Java source is put into target/generated-sources/releaseVersion
 *     in the package org.apache.uima.impl
 *
 */
public class UimaVersion {
  /**
   * @see org.apache.uima.UIMAFramework#_getMajorVersion()
   */
  public static short getMajorVersion() {
    return ${parsedVersion.majorVersion}; // major version
  }

  /**
   * @see org.apache.uima.UIMAFramework#_getMinorVersion()
   */
  public static short getMinorVersion() {
    return ${parsedVersion.minorVersion}; // minor version
  }

  /**
   * @see org.apache.uima.UIMAFramework#_getBuildRevision()
   */
  public static short getBuildRevision() {
    return ${parsedVersion.incrementalVersion}; // build revision
  }
  
  public static String getBuildYear() {
    return "${buildYear}";
  }
  

}