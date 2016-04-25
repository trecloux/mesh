package com.gentics.mesh.core.rest.node;

import java.util.HashMap;
import java.util.Map;

import com.gentics.mesh.core.rest.common.FieldTypes;
import com.gentics.mesh.core.rest.micronode.MicronodeResponse;
import com.gentics.mesh.core.rest.node.field.BinaryField;
import com.gentics.mesh.core.rest.node.field.Field;
import com.gentics.mesh.core.rest.node.field.MicronodeField;
import com.gentics.mesh.core.rest.node.field.NodeField;
import com.gentics.mesh.core.rest.node.field.impl.BinaryFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.BooleanFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.DateFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.HtmlFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.NumberFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.node.field.list.FieldList;
import com.gentics.mesh.core.rest.node.field.list.NodeFieldList;
import com.gentics.mesh.core.rest.node.field.list.impl.BooleanFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.DateFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.HtmlFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.NodeFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.NumberFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.StringFieldListImpl;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.ListFieldSchema;

/**
 * Convenience class which is used to classify a field map so that it can be referenced within the custom json deserializers.
 *
 */
public class FieldMapImpl extends HashMap<String, Field> implements FieldMap {

	private static final long serialVersionUID = 5375505652759811047L;

	public FieldMapImpl(Map<String, Field> map) {
		super(map);
	}

	public FieldMapImpl() {
	}

	@Override
	public DateFieldListImpl getDateFieldList(String key) {
		return (DateFieldListImpl) get(key);
	}

	@Override
	public <T extends Field> T get(String key, Class<T> classOfT) {
		return (T) get(key);
	}

	@Override
	public HtmlFieldListImpl getHtmlFieldList(String key) {
		return (HtmlFieldListImpl) get(key);
	}

	@Override
	public HtmlFieldImpl getHtmlField(String key) {
		return (HtmlFieldImpl) get(key);
	}

	@Override
	public BinaryField getBinaryField(String key) {
		return (BinaryFieldImpl) get(key);
	}

	@Override
	public BooleanFieldImpl getBooleanField(String key) {
		return (BooleanFieldImpl) get(key);
	}

	@Override
	public DateFieldImpl getDateField(String key) {
		return (DateFieldImpl) get(key);
	}

	@Override
	public MicronodeResponse getMicronodeField(String key) {
		return (MicronodeResponse) get(key);
	}

	@Override
	public NumberFieldImpl getNumberField(String key) {
		return (NumberFieldImpl) get(key);
	}

	@Override
	public NodeField getNodeField(String key) {
		return (NodeField) get(key);
	}

	@Override
	public NodeFieldListImpl getNodeListField(String key) {
		return (NodeFieldListImpl) get(key);
	}

	@Override
	public boolean hasField(String key) {
		return containsKey(key);
	}

	@Override
	public NumberFieldListImpl getNumberFieldList(String key) {
		return (NumberFieldListImpl) get(key);
	}

	@Override
	public BooleanFieldListImpl getBooleanListField(String key) {
		return (BooleanFieldListImpl) get(key);
	}

	@Override
	public StringFieldImpl getStringField(String key) {
		return (StringFieldImpl) get(key);
	}

	@Override
	public NodeResponse getNodeFieldExpanded(String key) {
		return (NodeResponse) get(key);
	}

	@Override
	public StringFieldListImpl getStringFieldList(String key) {
		return (StringFieldListImpl) get(key);
	}

	@Override
	public FieldList<MicronodeField> getMicronodeFieldList(String key) {
		return (FieldList<MicronodeField>) get(key);
	}

	@Override
	public NodeFieldList getNodeFieldList(String key) {
		return (NodeFieldList) get(key);
	}

	@Override
	public Field getField(String key, FieldSchema fieldSchema) {
		FieldTypes type = FieldTypes.valueByName(fieldSchema.getType());
		String listType = null;
		if (fieldSchema instanceof ListFieldSchema) {
			listType = ((ListFieldSchema) fieldSchema).getListType();
		}
		return getField(key, type, listType, false);
	}

	@Override
	public <T extends Field> T getField(String key, FieldTypes type, String listType, boolean expand) {
		return (T) get(key);
	}

	@Override
	public boolean containsKey(String key) {
		return super.containsKey(key);
	}

}