package com.gentics.mesh.core.field.string;

import static com.gentics.mesh.core.field.string.StringListFieldTestHelper.CREATE_EMPTY;
import static com.gentics.mesh.core.field.string.StringListFieldTestHelper.FETCH;
import static com.gentics.mesh.core.field.string.StringListFieldTestHelper.FILLTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.container.impl.NodeGraphFieldContainerImpl;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.field.GraphField;
import com.gentics.mesh.core.data.node.field.list.StringGraphFieldList;
import com.gentics.mesh.core.field.AbstractFieldTest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.field.Field;
import com.gentics.mesh.core.rest.node.field.list.impl.HtmlFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.StringFieldListImpl;
import com.gentics.mesh.core.rest.schema.ListFieldSchema;
import com.gentics.mesh.core.rest.schema.impl.ListFieldSchemaImpl;

public class StringListFieldTest extends AbstractFieldTest<ListFieldSchema> {

	private static final String STRING_LIST = "stringList";

	@Override
	protected ListFieldSchema createFieldSchema(boolean isRequired) {
		ListFieldSchema schema = new ListFieldSchemaImpl();
		schema.setListType("string");
		schema.setName(STRING_LIST);
		schema.setRequired(isRequired);
		return schema;
	}

	@Test
	@Override
	public void testFieldTransformation() throws Exception {

		Node node = folder("2015");
		prepareNode(node, "stringList", "string");

		NodeGraphFieldContainer container = node.getGraphFieldContainer(english());
		StringGraphFieldList stringList = container.createStringList("stringList");
		stringList.createString("dummyString1");
		stringList.createString("dummyString2");

		NodeResponse response = transform(node);
		assertList(2, "stringList", "string", response);

	}

	@Test
	@Override
	public void testFieldUpdate() throws Exception {
		NodeGraphFieldContainer container = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		StringGraphFieldList list = container.createStringList("dummyList");

		list.createString("1");
		assertEquals("dummyList", list.getFieldKey());
		assertNotNull(list.getList());

		assertEquals(1, list.getList().size());
		assertEquals(list.getSize(), list.getList().size());
		list.createString("2");
		assertEquals(2, list.getList().size());
		list.createString("3").setString("Some string 3");
		assertEquals(3, list.getList().size());
		assertEquals("Some string 3", list.getList().get(2).getString());

		StringGraphFieldList loadedList = container.getStringList("dummyList");
		assertNotNull(loadedList);
		assertEquals(3, loadedList.getSize());
		list.removeAll();
		assertEquals(0, list.getSize());
		assertEquals(0, list.getList().size());

	}

	@Test
	@Override
	public void testClone() {
		NodeGraphFieldContainer container = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		StringGraphFieldList testField = container.createStringList("testField");
		testField.createString("one");
		testField.createString("two");
		testField.createString("three");

		NodeGraphFieldContainerImpl otherContainer = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		testField.cloneTo(otherContainer);

		assertThat(otherContainer.getStringList("testField")).as("cloned field").isEqualToComparingFieldByField(testField);
	}

	@Test
	@Override
	public void testEquals() {
		NodeGraphFieldContainerImpl container = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		StringGraphFieldList fieldA = container.createStringList("fieldA");
		StringGraphFieldList fieldB = container.createStringList("fieldB");
		assertTrue("The field should  be equal to itself", fieldA.equals(fieldA));
		fieldA.addItem(fieldA.createString("testString"));
		assertTrue("The field should  still be equal to itself", fieldA.equals(fieldA));

		assertFalse("The field should not be equal to a non-string field", fieldA.equals("bogus"));
		assertFalse("The field should not be equal since fieldB has no value", fieldA.equals(fieldB));
		fieldB.addItem(fieldB.createString("testString"));
		assertTrue("Both fields have the same value and should be equal", fieldA.equals(fieldB));
	}

	@Test
	@Override
	public void testEqualsNull() {
		NodeGraphFieldContainerImpl container = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		StringGraphFieldList fieldA = container.createStringList("fieldA");
		assertFalse(fieldA.equals((Field) null));
		assertFalse(fieldA.equals((GraphField) null));
	}

	@Test
	@Override
	public void testEqualsRestField() {
		NodeGraphFieldContainer container = tx.getGraph().addFramedVertex(NodeGraphFieldContainerImpl.class);
		String dummyValue = "test123";

		// rest null - graph null
		StringGraphFieldList fieldA = container.createStringList(STRING_LIST);

		StringFieldListImpl restField = new StringFieldListImpl();
		assertTrue("Both fields should be equal to eachother since both values are null", fieldA.equals(restField));

		// rest set - graph set - different values
		fieldA.addItem(fieldA.createString(dummyValue));
		restField.add(dummyValue + 1L);
		assertFalse("Both fields should be different since both values are not equal", fieldA.equals(restField));

		// rest set - graph set - same value
		restField.getItems().clear();
		restField.add(dummyValue);
		assertTrue("Both fields should be equal since values are equal", fieldA.equals(restField));

		HtmlFieldListImpl otherTypeRestField = new HtmlFieldListImpl();
		otherTypeRestField.add(dummyValue);
		// rest set - graph set - same value different type
		assertFalse("Fields should not be equal since the type does not match.", fieldA.equals(otherTypeRestField));

	}

	@Test
	@Override
	public void testUpdateFromRestNullOnCreate() {
		invokeUpdateFromRestTestcase(STRING_LIST, FETCH, CREATE_EMPTY);
	}

	@Test
	@Override
	public void testUpdateFromRestNullOnCreateRequired() {
		invokeUpdateFromRestNullOnCreateRequiredTestcase(STRING_LIST, FETCH, CREATE_EMPTY);
	}

	@Test
	@Override
	public void testRemoveFieldViaNullValue() {
		InternalActionContext ac = getMockedInternalActionContext("");
		invokeRemoveFieldViaNullValueTestcase(STRING_LIST, FETCH, CREATE_EMPTY, (node) -> {
			StringFieldListImpl field = null;
			updateContainer(ac, node, STRING_LIST, field);
		});
	}

	@Test
	@Override
	public void testDeleteRequiredFieldViaNullValue() {
		InternalActionContext ac = getMockedInternalActionContext("");
		invokeDeleteRequiredFieldViaNullValueTestcase(STRING_LIST, FETCH, FILLTEXT, (container) -> {
			StringFieldListImpl field = null;
			updateContainer(ac, container, STRING_LIST, field);
		});
	}

	@Override
	public void testUpdateFromRestValidSimpleValue() {
		InternalActionContext ac = getMockedInternalActionContext("");
		invokeUpdateFromRestValidSimpleValueTestcase(STRING_LIST, FILLTEXT, (container) -> {
			StringFieldListImpl field = new StringFieldListImpl();
			field.getItems().add("someValue");
			field.getItems().add("someValue2");
			updateContainer(ac, container, STRING_LIST, field);
		} , (container) -> {
			StringGraphFieldList field = container.getStringList(STRING_LIST);
			assertNotNull("The graph field {" + STRING_LIST + "} could not be found.", field);
			assertEquals("The list of the field was not updated.", 2, field.getList().size());
			assertEquals("The list item of the field was not updated.", "someValue", field.getList().get(0).getString());
			assertEquals("The list item of the field was not updated.", "someValue2", field.getList().get(1).getString());
		});
	}

}