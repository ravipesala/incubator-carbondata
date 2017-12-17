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

package org.apache.carbondata.hadoop.api;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.carbondata.common.CarbonIterator;
import org.apache.carbondata.core.constants.CarbonCommonConstants;
import org.apache.carbondata.core.constants.CarbonLoadOptionConstants;
import org.apache.carbondata.core.metadata.datatype.StructField;
import org.apache.carbondata.core.metadata.datatype.StructType;
import org.apache.carbondata.core.metadata.schema.table.CarbonTable;
import org.apache.carbondata.core.metadata.schema.table.TableInfo;
import org.apache.carbondata.core.util.CarbonProperties;
import org.apache.carbondata.core.util.CarbonThreadFactory;
import org.apache.carbondata.hadoop.util.ObjectSerializationUtil;
import org.apache.carbondata.processing.loading.DataLoadExecutor;
import org.apache.carbondata.processing.loading.csvinput.StringArrayWritable;
import org.apache.carbondata.processing.loading.iterator.CarbonOutputIteratorWrapper;
import org.apache.carbondata.processing.loading.model.CarbonDataLoadSchema;
import org.apache.carbondata.processing.loading.model.CarbonLoadModel;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

/**
 * This is table level output format which writes the data to store in new segment. Each load
 * creates new segment folder and manages the folder through tablestatus file.
 * It also generate and writes dictionary data during load only if dictionary server is configured.
 */
// TODO Move dictionary generater which is coded in spark to MR framework.
public class CarbonTableOutputFormat extends FileOutputFormat<NullWritable, StringArrayWritable> {

  private static final String LOAD_MODEL = "mapreduce.carbontable.load.model";
  private static final String DATABASE_NAME = "mapreduce.carbontable.databaseName";
  private static final String TABLE_NAME = "mapreduce.carbontable.tableName";
  private static final String TABLE = "mapreduce.carbontable.table";
  private static final String TABLE_PATH = "mapreduce.carbontable.tablepath";
  private static final String INPUT_SCHEMA = "mapreduce.carbontable.inputschema";
  private static final String TEMP_STORE_LOCATIONS = "mapreduce.carbontable.tempstore.locations";
  private static final String OVERWRITE_SET = "mapreduce.carbontable.set.overwrite";
  public static final String COMPLEX_DELIMITERS = "mapreduce.carbontable.complex_delimiters";
  public static final String SERIALIZATION_NULL_FORMAT =
      "mapreduce.carbontable.serialization.null.format";
  public static final String BAD_RECORDS_LOGGER_ENABLE =
      "mapreduce.carbontable.bad.records.logger.enable";
  public static final String BAD_RECORDS_LOGGER_ACTION =
      "mapreduce.carbontable.bad.records.logger.action";
  public static final String IS_EMPTY_DATA_BAD_RECORD =
      "mapreduce.carbontable.empty.data.bad.record";
  public static final String SKIP_EMPTY_LINE = "mapreduce.carbontable.skip.empty.line";
  public static final String SORT_SCOPE = "mapreduce.carbontable.load.sort.scope";
  public static final String BATCH_SORT_SIZE_INMB =
      "mapreduce.carbontable.batch.sort.size.inmb";
  public static final String GLOBAL_SORT_PARTITIONS =
      "mapreduce.carbontable.global.sort.partitions";
  public static final String BAD_RECORD_PATH = "mapreduce.carbontable.bad.record.path";
  public static final String DATE_FORMAT = "mapreduce.carbontable.date.format";
  public static final String TIMESTAMP_FORMAT = "mapreduce.carbontable.timestamp.format";
  public static final String IS_ONE_PASS_LOAD = "mapreduce.carbontable.one.pass.load";
  public static final String DICTIONARY_SERVER_HOST =
      "mapreduce.carbontable.dict.server.host";
  public static final String DICTIONARY_SERVER_PORT =
      "mapreduce.carbontable.dict.server.port";

  private CarbonOutputCommitter committer;

  public static void setDatabaseName(Configuration configuration, String databaseName) {
    if (null != databaseName) {
      configuration.set(DATABASE_NAME, databaseName);
    }
  }

  public static String getDatabaseName(Configuration configuration) {
    return configuration.get(DATABASE_NAME);
  }

  public static void setTableName(Configuration configuration, String tableName) {
    if (null != tableName) {
      configuration.set(TABLE_NAME, tableName);
    }
  }

  public static String getTableName(Configuration configuration) {
    return configuration.get(TABLE_NAME);
  }

  public static void setTablePath(Configuration configuration, String tablePath) {
    if (null != tablePath) {
      configuration.set(TABLE_PATH, tablePath);
    }
  }

  public static String getTablePath(Configuration configuration) {
    return configuration.get(TABLE_PATH);
  }

  public static void setCarbonTable(Configuration configuration, CarbonTable carbonTable)
      throws IOException {
    if (carbonTable != null) {
      configuration.set(TABLE,
          ObjectSerializationUtil.convertObjectToString(carbonTable.getTableInfo().serialize()));
    }
  }

  public static CarbonTable getCarbonTable(Configuration configuration) throws IOException {
    CarbonTable carbonTable = null;
    String encodedString = configuration.get(TABLE);
    if (encodedString != null) {
      byte[] bytes = (byte[]) ObjectSerializationUtil.convertStringToObject(encodedString);
      TableInfo tableInfo = TableInfo.deserialize(bytes);
      carbonTable = CarbonTable.buildFromTableInfo(tableInfo);
    }
    return carbonTable;
  }

  public static void setLoadModel(Configuration configuration, CarbonLoadModel loadModel)
      throws IOException {
    if (loadModel != null) {
      configuration.set(LOAD_MODEL, ObjectSerializationUtil.convertObjectToString(loadModel));
    }
  }

  public static void setInputSchema(Configuration configuration, StructType inputSchema)
      throws IOException {
    if (inputSchema != null && inputSchema.getFields().size() > 0) {
      configuration.set(INPUT_SCHEMA, ObjectSerializationUtil.convertObjectToString(inputSchema));
    } else {
      throw new UnsupportedOperationException("Input schema must be set");
    }
  }

  private static StructType getInputSchema(Configuration configuration) throws IOException {
    String encodedString = configuration.get(INPUT_SCHEMA);
    if (encodedString != null) {
      return (StructType) ObjectSerializationUtil.convertStringToObject(encodedString);
    }
    return null;
  }

  public static boolean isOverwriteSet(Configuration configuration) {
    String overwrite = configuration.get(OVERWRITE_SET);
    if (overwrite != null) {
      return Boolean.parseBoolean(overwrite);
    }
    return false;
  }

  public static void setOverwrite(Configuration configuration, boolean overwrite) {
    configuration.set(OVERWRITE_SET, String.valueOf(overwrite));
  }

  public static void setTempStoreLocations(Configuration configuration, String[] tempLocations)
      throws IOException {
    if (tempLocations != null && tempLocations.length > 0) {
      configuration
          .set(TEMP_STORE_LOCATIONS, ObjectSerializationUtil.convertObjectToString(tempLocations));
    }
  }

  private static String[] getTempStoreLocations(TaskAttemptContext taskAttemptContext)
      throws IOException {
    String encodedString = taskAttemptContext.getConfiguration().get(TEMP_STORE_LOCATIONS);
    if (encodedString != null) {
      return (String[]) ObjectSerializationUtil.convertStringToObject(encodedString);
    }
    return new String[] {
        System.getProperty("java.io.tmpdir") + "/" + System.nanoTime() + "_" + taskAttemptContext
            .getTaskAttemptID().toString() };
  }

  @Override
  public synchronized OutputCommitter getOutputCommitter(TaskAttemptContext context)
      throws IOException {
    if (this.committer == null) {
      Path output = getOutputPath(context);
      this.committer = new CarbonOutputCommitter(output, context);
    }
    return this.committer;
  }

  @Override
  public RecordWriter<NullWritable, StringArrayWritable> getRecordWriter(
      TaskAttemptContext taskAttemptContext) throws IOException {
    final CarbonLoadModel loadModel = getLoadModel(taskAttemptContext.getConfiguration());
    loadModel.setTaskNo(System.nanoTime() + "");
    final String[] tempStoreLocations = getTempStoreLocations(taskAttemptContext);
    final CarbonOutputIteratorWrapper iteratorWrapper = new CarbonOutputIteratorWrapper();
    final DataLoadExecutor dataLoadExecutor = new DataLoadExecutor();
    ExecutorService executorService = Executors.newFixedThreadPool(1,
        new CarbonThreadFactory("CarbonRecordWriter:" + loadModel.getTableName()));;
    // It should be started in new thread as the underlying iterator uses blocking queue.
    Future future = executorService.submit(new Thread() {
      @Override public void run() {
        try {
          dataLoadExecutor
              .execute(loadModel, tempStoreLocations, new CarbonIterator[] { iteratorWrapper });
        } catch (Exception e) {
          dataLoadExecutor.close();
          throw new RuntimeException(e);
        }
      }
    });

    return new CarbonRecordWriter(iteratorWrapper, dataLoadExecutor, loadModel, future,
        executorService);
  }

  public static CarbonLoadModel getLoadModel(Configuration conf) throws IOException {
    CarbonLoadModel model;
    String encodedString = conf.get(LOAD_MODEL);
    if (encodedString != null) {
      model = (CarbonLoadModel) ObjectSerializationUtil.convertStringToObject(encodedString);
      return model;
    }
    model = new CarbonLoadModel();
    CarbonProperties carbonProperty = CarbonProperties.getInstance();
    model.setDatabaseName(CarbonTableOutputFormat.getDatabaseName(conf));
    model.setTableName(CarbonTableOutputFormat.getTableName(conf));
    model.setCarbonDataLoadSchema(new CarbonDataLoadSchema(getCarbonTable(conf)));
    model.setTablePath(getTablePath(conf));

    setFileHeader(conf, model);
    model.setSerializationNullFormat(conf.get(SERIALIZATION_NULL_FORMAT, "\\N"));
    model.setBadRecordsLoggerEnable(
        conf.get(
            BAD_RECORDS_LOGGER_ENABLE,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_BAD_RECORDS_LOGGER_ENABLE,
                CarbonLoadOptionConstants.CARBON_OPTIONS_BAD_RECORDS_LOGGER_ENABLE_DEFAULT)));
    model.setBadRecordsAction(
        conf.get(
            BAD_RECORDS_LOGGER_ACTION,
            carbonProperty.getProperty(
                CarbonCommonConstants.CARBON_BAD_RECORDS_ACTION,
                CarbonCommonConstants.CARBON_BAD_RECORDS_ACTION_DEFAULT)));

    model.setIsEmptyDataBadRecord(
        conf.get(
            IS_EMPTY_DATA_BAD_RECORD,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_IS_EMPTY_DATA_BAD_RECORD,
                CarbonLoadOptionConstants.CARBON_OPTIONS_IS_EMPTY_DATA_BAD_RECORD_DEFAULT)));

    model.setSkipEmptyLine(
        conf.get(
            SKIP_EMPTY_LINE,
            carbonProperty.getProperty(CarbonLoadOptionConstants.CARBON_OPTIONS_SKIP_EMPTY_LINE)));

    String complexDelim = conf.get(COMPLEX_DELIMITERS, "\\$" + "," + "\\:");
    String[] split = complexDelim.split(",");
    model.setComplexDelimiterLevel1(split[0]);
    if (split.length > 1) {
      model.setComplexDelimiterLevel1(split[1]);
    }
    model.setDateFormat(
        conf.get(
            DATE_FORMAT,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_DATEFORMAT,
                CarbonLoadOptionConstants.CARBON_OPTIONS_DATEFORMAT_DEFAULT)));

    model.setTimestampformat(
        conf.get(
            TIMESTAMP_FORMAT,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_TIMESTAMPFORMAT,
                CarbonLoadOptionConstants.CARBON_OPTIONS_TIMESTAMPFORMAT_DEFAULT)));

    model.setGlobalSortPartitions(
        conf.get(
            GLOBAL_SORT_PARTITIONS,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_GLOBAL_SORT_PARTITIONS,
                null)));

    model.setBatchSortSizeInMb(
        conf.get(
            BATCH_SORT_SIZE_INMB,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_BATCH_SORT_SIZE_INMB,
                carbonProperty.getProperty(
                    CarbonCommonConstants.LOAD_BATCH_SORT_SIZE_INMB,
                    CarbonCommonConstants.LOAD_BATCH_SORT_SIZE_INMB_DEFAULT))));

    model.setBadRecordsLocation(
        conf.get(BAD_RECORD_PATH,
            carbonProperty.getProperty(
                CarbonLoadOptionConstants.CARBON_OPTIONS_BAD_RECORD_PATH,
                carbonProperty.getProperty(
                    CarbonCommonConstants.CARBON_BADRECORDS_LOC,
                    CarbonCommonConstants.CARBON_BADRECORDS_LOC_DEFAULT_VAL))));

    model.setUseOnePass(
        conf.getBoolean(IS_ONE_PASS_LOAD,
            Boolean.parseBoolean(
                carbonProperty.getProperty(
                    CarbonLoadOptionConstants.CARBON_OPTIONS_SINGLE_PASS,
                    CarbonLoadOptionConstants.CARBON_OPTIONS_SINGLE_PASS_DEFAULT))));
    return model;
  }

  private static void setFileHeader(Configuration configuration, CarbonLoadModel model)
      throws IOException {
    StructType inputSchema = getInputSchema(configuration);
    if (inputSchema == null || inputSchema.getFields().size() == 0) {
      throw new UnsupportedOperationException("Input schema must be set");
    }
    List<StructField> fields = inputSchema.getFields();
    StringBuilder builder = new StringBuilder();
    String[] columns = new String[fields.size()];
    int i = 0;
    for (StructField field : fields) {
      builder.append(field.getFieldName());
      builder.append(",");
      columns[i++] = field.getFieldName();
    }
    String header = builder.toString();
    model.setCsvHeader(header.substring(0, header.length() - 1));
    model.setCsvHeaderColumns(columns);
  }

  public static class CarbonRecordWriter extends RecordWriter<NullWritable, StringArrayWritable> {

    private CarbonOutputIteratorWrapper iteratorWrapper;

    private DataLoadExecutor dataLoadExecutor;

    private CarbonLoadModel loadModel;

    private ExecutorService executorService;

    private Future future;

    public CarbonRecordWriter(CarbonOutputIteratorWrapper iteratorWrapper,
        DataLoadExecutor dataLoadExecutor, CarbonLoadModel loadModel, Future future,
        ExecutorService executorService) {
      this.iteratorWrapper = iteratorWrapper;
      this.dataLoadExecutor = dataLoadExecutor;
      this.loadModel = loadModel;
      this.executorService = executorService;
      this.future = future;
    }

    @Override public void write(NullWritable aVoid, StringArrayWritable strings)
        throws InterruptedException {
      iteratorWrapper.write(strings.get());
    }

    @Override public void close(TaskAttemptContext taskAttemptContext) throws InterruptedException {
      iteratorWrapper.close();
      try {
        future.get();
      } catch (ExecutionException e) {
        throw new InterruptedException(e.getMessage());
      } finally {
        executorService.shutdownNow();
        dataLoadExecutor.close();
      }
    }

    public CarbonLoadModel getLoadModel() {
      return loadModel;
    }
  }
}
