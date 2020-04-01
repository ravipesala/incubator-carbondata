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

package org.apache.carbondata.tranaction;

import org.apache.carbondata.core.datastore.impl.FileFactory;
import org.apache.carbondata.core.statusmanager.LoadMetadataDetails;
import org.apache.carbondata.core.statusmanager.SegmentStatus;
import org.apache.carbondata.core.transaction.TransactionAction;
import org.apache.carbondata.core.util.path.CarbonTablePath;
import org.apache.carbondata.events.OperationContext;
import org.apache.carbondata.processing.loading.model.CarbonLoadModel;
import org.apache.carbondata.processing.util.CarbonLoaderUtil;
import org.apache.carbondata.spark.rdd.CarbonDataRDDFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.spark.sql.SparkSession;

public class LoadTransactionActions implements TransactionAction {

  private SparkSession sparkSession;

  private CarbonLoadModel carbonLoadModel;

  private LoadMetadataDetails loadMetadataDetails;

  private boolean overwriteTable;

  private String uuid;

  private String segmentFileName;

  private OperationContext operationContext;

  private Configuration configuration;

  public LoadTransactionActions(SparkSession sparkSession, CarbonLoadModel carbonLoadModel,
      LoadMetadataDetails loadMetadataDetails, boolean overwriteTable, String uuid,
      String segmentFileName, OperationContext operationContext, Configuration configuration) {
    this.sparkSession = sparkSession;
    this.carbonLoadModel = carbonLoadModel;
    this.loadMetadataDetails = loadMetadataDetails;
    this.overwriteTable = overwriteTable;
    this.uuid = uuid;
    this.segmentFileName = segmentFileName;
    this.operationContext = operationContext;
    this.configuration = configuration;
  }

  public void commit() throws Exception {
    SegmentStatus segmentStatus = loadMetadataDetails.getSegmentStatus();
    if (segmentStatus == SegmentStatus.MARKED_FOR_DELETE
        || segmentStatus == SegmentStatus.LOAD_FAILURE) {
      throw new Exception("Failed to commit transaction:");
    }
    CarbonLoaderUtil.writeTableStatus(carbonLoadModel, loadMetadataDetails, overwriteTable, uuid);
    CarbonDataRDDFactory
        .handlePostEvent(carbonLoadModel, operationContext, uuid, true, segmentFileName,
            sparkSession, loadMetadataDetails.getSegmentStatus(), configuration);
  }

  public void rollback() throws Exception {
    SegmentStatus segmentStatus = loadMetadataDetails.getSegmentStatus();
    if (segmentStatus == SegmentStatus.MARKED_FOR_DELETE
        || segmentStatus == SegmentStatus.LOAD_FAILURE) {
      return;
    }
    CarbonLoaderUtil.updateTableStatusForFailure(carbonLoadModel, uuid);
    CarbonLoaderUtil
        .deleteSegment(carbonLoadModel, Integer.parseInt(carbonLoadModel.getSegmentId()));
    // delete corresponding segment file from metadata
    String segmentFile =
        CarbonTablePath.getSegmentFilesLocation(carbonLoadModel.getTablePath()) + "/"
            + segmentFileName;
    FileFactory.deleteFile(segmentFile);
  }
}
