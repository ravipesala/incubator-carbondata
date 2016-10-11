package org.apache.carbondata.processing.newflow;

import java.util.Iterator;

import org.apache.carbondata.core.carbon.AbsoluteTableIdentifier;

public class CarbonDataLoadConfiguration {

  private DataField[] dataFields;

  private String tempStoreLocation;

  private String blockletSize;

  private String batchSize;

  private String sortSize;

  private String[] header;

  private AbsoluteTableIdentifier tableIdentifier;

  private Iterator<Object[]> inputIterator;

  private String[] complexDelimiters;

  private String partitionId;

  private String segmentId;

  private String taskNo;

  private String factTimeStamp;

  private RunTimeDataLoadConfiguration runTimeDataLoadConfiguration =
      new RunTimeDataLoadConfiguration();

  public int getDimensionCount() {
    int dimCount = 0;
    for (int i = 0; i < dataFields.length; i++) {
      if (dataFields[i].getColumn().isDimesion()) {
        dimCount++;
      }
    }
    return dimCount;
  }

  public int getNoDictionaryCount() {
    int dimCount = 0;
    for (int i = 0; i < dataFields.length; i++) {
      if (dataFields[i].getColumn().isDimesion() && !dataFields[i].hasDictionaryEncoding()) {
        dimCount++;
      }
    }
    return dimCount;
  }

  public int getComplexDimensionCount() {
    int dimCount = 0;
    for (int i = 0; i < dataFields.length; i++) {
      if (dataFields[i].getColumn().isComplex()) {
        dimCount++;
      }
    }
    return dimCount;
  }

  public int getMeasureCount() {
    int msrCount = 0;
    for (int i = 0; i < dataFields.length; i++) {
      if (!dataFields[i].getColumn().isDimesion()) {
        msrCount++;
      }
    }
    return msrCount;
  }

  public DataField[] getDataFields() {
    return dataFields;
  }

  public void setDataFields(DataField[] dataFields) {
    this.dataFields = dataFields;
  }

  public String getTempStoreLocation() {
    return tempStoreLocation;
  }

  public void setTempStoreLocation(String tempStoreLocation) {
    this.tempStoreLocation = tempStoreLocation;
  }

  public String getBlockletSize() {
    return blockletSize;
  }

  public void setBlockletSize(String blockletSize) {
    this.blockletSize = blockletSize;
  }

  public String getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(String batchSize) {
    this.batchSize = batchSize;
  }

  public String getSortSize() {
    return sortSize;
  }

  public void setSortSize(String sortSize) {
    this.sortSize = sortSize;
  }

  public Iterator<Object[]> getInputIterator() {
    return inputIterator;
  }

  public void setInputIterator(Iterator<Object[]> inputIterator) {
    this.inputIterator = inputIterator;
  }

  public String[] getHeader() {
    return header;
  }

  public void setHeader(String[] header) {
    this.header = header;
  }

  public AbsoluteTableIdentifier getTableIdentifier() {
    return tableIdentifier;
  }

  public void setTableIdentifier(AbsoluteTableIdentifier tableIdentifier) {
    this.tableIdentifier = tableIdentifier;
  }

  public String[] getComplexDelimiters() {
    return complexDelimiters;
  }

  public void setComplexDelimiters(String[] complexDelimiters) {
    this.complexDelimiters = complexDelimiters;
  }

  public String getPartitionId() {
    return partitionId;
  }

  public void setPartitionId(String partitionId) {
    this.partitionId = partitionId;
  }

  public String getSegmentId() {
    return segmentId;
  }

  public void setSegmentId(String segmentId) {
    this.segmentId = segmentId;
  }

  public String getTaskNo() {
    return taskNo;
  }

  public void setTaskNo(String taskNo) {
    this.taskNo = taskNo;
  }

  public RunTimeDataLoadConfiguration getRunTimeDataLoadConfiguration() {
    return runTimeDataLoadConfiguration;
  }

  public String getFactTimeStamp() {
    return factTimeStamp;
  }

  public void setFactTimeStamp(String factTimeStamp) {
    this.factTimeStamp = factTimeStamp;
  }
}
