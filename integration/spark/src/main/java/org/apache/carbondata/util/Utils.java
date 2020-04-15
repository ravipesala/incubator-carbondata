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

package org.apache.carbondata.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.indexstore.PartitionSpec;
import org.apache.carbondata.core.metadata.schema.table.CarbonTable;
import org.apache.carbondata.core.util.CarbonProperties;
import org.apache.carbondata.hadoop.CarbonInputSplit;
import org.apache.carbondata.hadoop.CarbonMultiBlockSplit;
import org.apache.carbondata.spark.rdd.CarbonSparkPartition;

public class Utils {

  public static List<CarbonSparkPartition> mergeSplit(List<CarbonSparkPartition> allPartition,
      CarbonTable carbonTable) {
    if (allPartition.isEmpty()) {
      return allPartition;
    }
    long configuredSize = carbonTable.getBlockSizeInMB() * 1024L * 1024L;
    int minThreashold;
    try {
      minThreashold = Integer.parseInt(CarbonProperties.getInstance()
          .getProperty(CarbonCommonConstants.CARBON_MIN_THREASHOLD_FOR_SEGMENT_MERGER,
              CarbonCommonConstants.CARBON_MIN_THREASHOLD_FOR_SEGMENT_MERGER_DEFAULT));
    } catch (NumberFormatException exp) {
      minThreashold =
          Integer.parseInt(CarbonCommonConstants.CARBON_MIN_THREASHOLD_FOR_SEGMENT_MERGER_DEFAULT);
    }
    if (!carbonTable.isHivePartitionTable()) {
      return mergeSplitBasedOnSize(allPartition, configuredSize, minThreashold);
    }
    Map<PartitionSpec, List<CarbonSparkPartition>> partitionSpecToParitionMap = new HashMap<>();
    for (CarbonSparkPartition carbonSparkPartition : allPartition) {
      List<CarbonSparkPartition> carbonSparkPartitions =
          partitionSpecToParitionMap.get(carbonSparkPartition.partitionSpec().get());
      if (null == carbonSparkPartitions) {
        carbonSparkPartitions = new ArrayList<>();
        partitionSpecToParitionMap
            .put(carbonSparkPartition.partitionSpec().get(), carbonSparkPartitions);
      }
      carbonSparkPartitions.add(carbonSparkPartition);
    }
    Iterator<Map.Entry<PartitionSpec, List<CarbonSparkPartition>>> iterator =
        partitionSpecToParitionMap.entrySet().iterator();
    List<List<CarbonSparkPartition>> rejectedList = new ArrayList<>();
    List<List<CarbonSparkPartition>> selectedList = new ArrayList<>();
    int totalNumberOfPartition = partitionSpecToParitionMap.size();
    while (iterator.hasNext()) {
      Map.Entry<PartitionSpec, List<CarbonSparkPartition>> entry = iterator.next();
      List<CarbonSparkPartition> carbonSparkPartitions =
          mergeSplitBasedOnSize(entry.getValue(), configuredSize, minThreashold);
      if (carbonSparkPartitions.isEmpty()) {
        rejectedList.add(carbonSparkPartitions);
      } else {
        selectedList.add(carbonSparkPartitions);
      }
    }
    boolean isRejectPresent = rejectedList.size() > 0;
    if (isRejectPresent) {
      int rejectedPercentage = (rejectedList.size() / totalNumberOfPartition) * 100;
      if (rejectedPercentage > minThreashold) {
        return new ArrayList<>();
      }
    }
    List<CarbonSparkPartition> collect =
        selectedList.stream().flatMap(List::stream).collect(Collectors.toList());
    List<CarbonSparkPartition> result = new ArrayList<>();
    int rddId = allPartition.get(0).rddId();
    int counter = 0;
    for (CarbonSparkPartition carbonSparkPartition : collect) {
      result.add(new CarbonSparkPartition(rddId, counter++, carbonSparkPartition.multiBlockSplit(),
          carbonSparkPartition.partitionSpec()));
    }
    return result;
  }

  private static List<CarbonSparkPartition> mergeSplitBasedOnSize(
      List<CarbonSparkPartition> allPartition, long configuredSize, int minThreashold) {
    List<CarbonSparkPartition> result = new ArrayList<>();
    List<CarbonInputSplit> allSplits = new ArrayList<>();
    for (CarbonSparkPartition sparkPartition : allPartition) {
      allSplits.addAll(sparkPartition.multiBlockSplit().getAllSplits());
    }
    long totalSize = 0;
    long minSizeToConsider = (configuredSize * 80) / 100;
    long fileExceedingThreashold = 0;
    for (CarbonInputSplit split : allSplits) {
      totalSize += split.getLength();
      if (split.getLength() >= minSizeToConsider) {
        fileExceedingThreashold++;
      }
    }
    if (configuredSize > totalSize) {
      List<String> locations = new ArrayList<>();
      for (CarbonInputSplit split : allSplits) {
        try {
          locations.addAll(Arrays.asList(split.getLocations()));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      result.add(new CarbonSparkPartition(0, 0,
          new CarbonMultiBlockSplit(allSplits, locations.toArray(new String[locations.size()])),
          allPartition.get(0).partitionSpec()));
      return result;
    }
    int actualMinThreashold = (allSplits.size() * minThreashold) / 100;
    if (fileExceedingThreashold >= actualMinThreashold) {
      return new ArrayList<>();
    }
    long noOfGroup = totalSize / configuredSize;
    long leftOver = totalSize % configuredSize;
    int runningIndex = 0;
    long currentSize;
    List<List<CarbonInputSplit>> splitsGroup = new ArrayList<>();
    List<List<String>> splitLocations = new ArrayList<>();
    for (int i = 0; i < noOfGroup; i++) {
      currentSize = 0;
      List<CarbonInputSplit> splits = new ArrayList<>();
      List<String> locations = new ArrayList<>();
      for (int j = runningIndex; j < allSplits.size(); j++) {
        splits.add(allSplits.get(j));
        currentSize += allSplits.get(j).getLength();
        try {
          locations.addAll(Arrays.asList(allSplits.get(j).getLocations()));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        runningIndex++;
        if (currentSize >= configuredSize) {
          break;
        }
      }
      splitsGroup.add(splits);
      splitLocations.add(locations);
    }
    if (leftOver > 0) {
      splitsGroup.get(0).add(allSplits.get(allSplits.size() - 1));
      try {
        splitLocations.get(0)
            .addAll(Arrays.asList(allSplits.get(allSplits.size() - 1).getLocations()));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    int counter = 0;
    for (List<CarbonInputSplit> splits : splitsGroup) {
      String[] loc =
          splitLocations.get(counter).toArray(new String[splitLocations.get(counter).size()]);
      result.add(new CarbonSparkPartition(counter, counter, new CarbonMultiBlockSplit(splits, loc),
          allPartition.get(0).partitionSpec()));
    }
    return result;
  }
}
