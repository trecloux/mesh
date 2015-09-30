package com.gentics.mesh.core.rest.schema;

public interface DateFieldSchema extends MicroschemaListableFieldSchema {

	/**
	 * Set the date field schema value.
	 * 
	 * @param date
	 */
	void setDate(String date);

	/**
	 * Return the date field schema value.
	 * 
	 * @return
	 */
	String getDate();
}
