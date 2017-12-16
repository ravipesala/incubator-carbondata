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
package org.apache.carbondata.core.indexstore.blockletindex;

import java.util.List;

import org.apache.carbondata.core.datamap.dev.DataMapModel;

public class BlockletDataMapModel extends DataMapModel {

  private byte[] fileData;

  private List<String> partitions;

  private boolean partitionedSegment;

  public BlockletDataMapModel(String filePath, byte[] fileData, List<String> partitions,
      boolean partitionedSegment) {
    super(filePath);
    this.fileData = fileData;
    this.partitions = partitions;
    this.partitionedSegment = partitionedSegment;
  }

  public byte[] getFileData() {
    return fileData;
  }

  public List<String> getPartitions() {
    return partitions;
  }

  public boolean isPartitionedSegment() {
    return partitionedSegment;
  }
}
