package com.thinkbiganalytics.spark.dataprofiler.columns;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.spark.sql.types.StructField;

import com.thinkbiganalytics.spark.dataprofiler.core.ProfilerConfiguration;
import com.thinkbiganalytics.spark.dataprofiler.model.MetricType;
import com.thinkbiganalytics.spark.dataprofiler.output.OutputRow;
import com.thinkbiganalytics.spark.dataprofiler.output.OutputWriter;
import com.thinkbiganalytics.spark.dataprofiler.topn.TopNDataItem;
import com.thinkbiganalytics.spark.dataprofiler.topn.TopNDataList;

/**
 * Class to hold common profile statistics for columns of all data types
 * @author jagrut sharma
 *
 */
@SuppressWarnings("serial")
public abstract class ColumnStatistics implements Serializable {
	
	/* Schema information for column */
	protected StructField columnField;
	
	/* Common metrics for all data types */
	protected long nullCount;
	protected long totalCount;
	protected long uniqueCount;
	protected double percNullValues;
	protected double percUniqueValues;
	protected double percDuplicateValues;
	protected TopNDataList topNValues; 
	
	/* Other variables */
	protected DecimalFormat df;
	protected OutputWriter outputWriter; 
	protected List<OutputRow> rows;
	

	/**
	 * One-argument constructor
	 * @param columnField field schema
	 */
	protected ColumnStatistics(StructField columnField) {
		this.columnField = columnField;
		nullCount = 0;
		totalCount = 0;
		uniqueCount = 0;
		percNullValues = 0.0d;
		percUniqueValues = 0.0d;
		percDuplicateValues = 0.0d;
		topNValues = new TopNDataList();
		outputWriter = OutputWriter.getInstance();
		df = new DecimalFormat(getDecimalFormatPattern());
	}

	
	/**
	 * Calculate common statistics by accommodating the value and frequency/count
	 * @param columnValue value
	 * @param columnCount frequency/count
	 */
	protected void accomodateCommon(Object columnValue, Long columnCount) {
		
		totalCount += columnCount;
		uniqueCount += 1;
		
		if (columnValue == null) {
			nullCount += columnCount;
		}
		
		doPercentageCalculationsCommon();
		
		topNValues.add(columnValue, columnCount);
	}
	
	
	/**
	 * Combine with another column statistics
	 * @param v_columnStatistics column statistics to combine with
	 */
	protected void combineCommon(ColumnStatistics v_columnStatistics) {
		
		totalCount += v_columnStatistics.totalCount;
		uniqueCount += v_columnStatistics.uniqueCount;
		nullCount += v_columnStatistics.nullCount;
		
		doPercentageCalculationsCommon();
		
		for (TopNDataItem dataItem:
				v_columnStatistics.topNValues.getTopNDataItemsForColumnInReverse()) {
			topNValues.add(dataItem.getValue(), dataItem.getCount());
		}
	}
	

	/**
	 * Write column's schema information for output result table
	 */
	protected void writeColumnSchemaInformation() {
		
		rows = new ArrayList<OutputRow>();
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.COLUMN_DATATYPE), String.valueOf(columnField.dataType())));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.COLUMN_NULLABLE), String.valueOf(columnField.nullable())));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.COLUMN_METADATA), String.valueOf(columnField.metadata())));
		outputWriter.addRows(rows);

	}
	
	
	/**
	 * Print column's schema information to console
	 * @return schema information
	 */
	protected String getVerboseColumnSchemaInformation() {
		
		String retVal = "ColumnInfo ["
				+ "name=" + columnField.name()
				+ ", datatype=" + columnField.dataType().simpleString()
				+ ", nullable=" + columnField.nullable()
				+ ", metadata=" + columnField.metadata()
				+ "]";
		
		return retVal;
	}
	
	
	/**
	 * Write top n rows in column for output result table 
	 */
	protected void writeTopNInformation() {
		
		rows = new ArrayList<OutputRow>();
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.TOP_N_VALUES), topNValues.printTopNItems(ProfilerConfiguration.NUMBER_OF_TOP_N_VALUES)));
		outputWriter.addRows(rows);
	}
	
	
	/**
	 * Print top n rows in column to console
	 * @return top n rows
	 */
	protected String getVerboseTopNInformation() {
		
		String retVal = "Top " + ProfilerConfiguration.NUMBER_OF_TOP_N_VALUES + " values [\n"
				+ topNValues.printTopNItems(ProfilerConfiguration.NUMBER_OF_TOP_N_VALUES)
				+ "]";
		
		return retVal;
	}
	
	
	
	/**
	 * Write common statistics information for output result table
	 */
	protected void writeStatisticsCommon() {
		
		writeColumnSchemaInformation();
		
		rows = new ArrayList<OutputRow>();
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.NULL_COUNT), String.valueOf(nullCount)));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.TOTAL_COUNT), String.valueOf(totalCount)));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.UNIQUE_COUNT), String.valueOf(uniqueCount)));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.PERC_NULL_VALUES), df.format(percNullValues)));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.PERC_UNIQUE_VALUES), df.format(percUniqueValues)));
		rows.add(new OutputRow(columnField.name(), String.valueOf(MetricType.PERC_DUPLICATE_VALUES), df.format(percDuplicateValues)));
		outputWriter.addRows(rows);
		
		writeTopNInformation();
	}
	
	
	
	/**
	 * Print common statistics information to console
	 * @return common statistics
	 */
	protected String getVerboseStatisticsCommon() {
			
		String retVal = getVerboseColumnSchemaInformation() 
				+ "\n"
				+ "CommonStatistics ["
				+ "nullCount=" + nullCount 
				+ ", totalCount=" + totalCount
				+ ", uniqueCount=" + uniqueCount
				+ ", percNullValues=" + df.format(percNullValues)
				+ ", percUniqueValues=" + df.format(percUniqueValues)
				+ ", percDuplicateValues=" + df.format(percDuplicateValues)
				+ "]"
				+ "\n"
				+ getVerboseTopNInformation();
				
		return retVal;
	}
	
	
	/*
	 * Do percentage calculations for common metrics
	 */
	private void doPercentageCalculationsCommon() {
		
		percNullValues = ((double) nullCount / totalCount) * 100;
		percUniqueValues = ((double) uniqueCount / totalCount) * 100;
		percDuplicateValues = 100.0d - percUniqueValues;
	}
	

	/*
	 * Build format to display decimals up to configured number of digits
	 */
	private String getDecimalFormatPattern() {
		
		StringBuilder format = new StringBuilder(); 
		format.append("#.");
		
		for (int i = 0; i < ProfilerConfiguration.DECIMAL_DIGITS_TO_DISPLAY_CONSOLE_OUTPUT; i++) {
			format.append("#");
		}
		
		return format.toString();
	}
	
	
	/**
	 * Get null count
	 * @return null count
	 */
	public long getNullCount() {
		return nullCount;
	}

	
	/**
	 * Get total count (includes nulls and empty values)
	 * @return total count
	 */
	public long getTotalCount() {
		return totalCount;
	}

	
	
	/**
	 * Get unique count (null and empty are considered a unique value each)
	 * @return unique count
	 */
	public long getUniqueCount() {
		return uniqueCount;
	}


	/**
	 * Get percentage of null values
	 * @return percentage of null values
	 */
	public double getPercNullValues() {
		return percNullValues;
	}


	/**
	 * Get percentage of unique values
	 * @return percentage of unique values
	 */
	public double getPercUniqueValues() {
		return percUniqueValues;
	}


	/**
	 * Get percentage of duplicate values
	 * @return percentage of duplicate values
	 */
	public double getPercDuplicateValues() {
		return percDuplicateValues;
	}


	/**
	 * Get top n values (in order of frequency)
	 * @return top n values
	 */
	public TopNDataList getTopNValues() {
		return topNValues;
	}

	/* 
	 * Methods to be implemented by data type specific column statistics classes that:
	 * 1) extend this class
	 * 2) may implement additional metrics 
	 * 
	 */
	public abstract void accomodate(Object columnValue, Long columnCount);

	public abstract void combine(ColumnStatistics v_columnStatistics);
	
	public abstract void writeStatistics();
	
	public abstract String getVerboseStatistics();

}