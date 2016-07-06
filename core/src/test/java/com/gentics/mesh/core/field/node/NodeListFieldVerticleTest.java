package com.gentics.mesh.core.field.node;

import static com.gentics.mesh.util.MeshAssert.latchFor;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.node.field.list.NodeGraphFieldList;
import com.gentics.mesh.core.data.node.field.list.impl.NodeGraphFieldListImpl;
import com.gentics.mesh.core.field.AbstractGraphListFieldVerticleTest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.field.Field;
import com.gentics.mesh.core.rest.node.field.NodeFieldListItem;
import com.gentics.mesh.core.rest.node.field.list.NodeFieldList;
import com.gentics.mesh.core.rest.node.field.list.impl.NodeFieldListImpl;
import com.gentics.mesh.core.rest.node.field.list.impl.NodeFieldListItemImpl;

import io.vertx.core.Future;

public class NodeListFieldVerticleTest extends AbstractGraphListFieldVerticleTest {

	@Override
	public String getListFieldType() {
		return "node";
	}

	@Test
	public void testCreateNodeWithNoField() {
		NodeResponse response = createNode(null, (Field) null);
		NodeFieldList nodeField = response.getFields().getNodeFieldList(FIELD_NAME);
		assertNull(nodeField);
	}

	@Test
	public void testBogusNodeList() throws IOException {
		NodeFieldListImpl listField = new NodeFieldListImpl();
		listField.add(new NodeFieldListItemImpl("bogus"));

		Future<NodeResponse> future = createNodeAsync("listField", listField);
		latchFor(future);
		expectException(future, BAD_REQUEST, "node_list_item_not_found", "bogus");
	}

	@Test
	public void testValidNodeList() throws IOException {
		NodeFieldListImpl listField = new NodeFieldListImpl();
		listField.add(new NodeFieldListItemImpl(content().getUuid()));
		listField.add(new NodeFieldListItemImpl(folder("news").getUuid()));

		NodeResponse response = createNode("listField", listField);

		NodeFieldList listFromResponse = response.getFields().getNodeFieldList("listField");
		assertEquals(2, listFromResponse.getItems().size());
		assertEquals(content().getUuid(), listFromResponse.getItems().get(0).getUuid());
		assertEquals(folder("news").getUuid(), listFromResponse.getItems().get(1).getUuid());

	}

	@Test
	public void testNullNodeList2() throws IOException {
		NodeFieldListImpl listField = new NodeFieldListImpl();
		listField.add(new NodeFieldListItemImpl(null));
	}

	@Test
	public void testUpdateNodeFieldWithField() {
		Node node = folder("2015");
		Node targetNode = folder("news");
		Node targetNode2 = folder("deals");

		List<List<Node>> valueCombinations = Arrays.asList(Arrays.asList(targetNode), Arrays.asList(targetNode2, targetNode), Collections.emptyList(),
				Arrays.asList(targetNode, targetNode2), Arrays.asList(targetNode2));

		NodeGraphFieldContainer container = node.getGraphFieldContainer("en");
		for (int i = 0; i < 20; i++) {
			List<Node> oldValue = getListValues(container, NodeGraphFieldListImpl.class, FIELD_NAME);
			List<Node> newValue = valueCombinations.get(i % valueCombinations.size());

			NodeFieldListImpl list = new NodeFieldListImpl();
			for (Node value : newValue) {
				list.add(new NodeFieldListItemImpl(value.getUuid()));
			}
			NodeResponse response = updateNode(FIELD_NAME, list);
			NodeFieldList field = response.getFields().getNodeFieldList(FIELD_NAME);
			assertThat(field.getItems()).as("Updated field").usingElementComparatorOnFields("uuid").containsExactlyElementsOf(list.getItems());
			node.reload();
			container.reload();

			NodeGraphFieldContainer newContainerVersion = container.getNextVersion();
			assertEquals("Check version number", newContainerVersion.getVersion().toString(), response.getVersion().getNumber());
			assertEquals("Check old value", oldValue, getListValues(container, NodeGraphFieldListImpl.class, FIELD_NAME));
			container = newContainerVersion;
		}
	}

	@Test
	public void testUpdateSameValue() {
		Node targetNode = folder("news");
		Node targetNode2 = folder("deals");

		NodeFieldListImpl list = new NodeFieldListImpl();
		list.add(new NodeFieldListItemImpl(targetNode.getUuid()));
		list.add(new NodeFieldListItemImpl(targetNode2.getUuid()));
		NodeResponse firstResponse = updateNode(FIELD_NAME, list);
		String oldNumber = firstResponse.getVersion().getNumber();

		NodeResponse secondResponse = updateNode(FIELD_NAME, list);
		assertThat(secondResponse.getVersion().getNumber()).as("New version number").isEqualTo(oldNumber);
	}

	@Test
	public void testUpdateSetNull() {
		Node targetNode = folder("news");
		Node targetNode2 = folder("deals");

		NodeFieldListImpl list = new NodeFieldListImpl();
		list.add(new NodeFieldListItemImpl(targetNode.getUuid()));
		list.add(new NodeFieldListItemImpl(targetNode2.getUuid()));
		updateNode(FIELD_NAME, list);

		NodeResponse secondResponse = updateNode(FIELD_NAME, null);
		assertThat(secondResponse.getFields().getNodeFieldList(FIELD_NAME)).as("Updated Field").isNull();

	}

	@Test
	public void testUpdateSetEmpty() {
		Node targetNode = folder("news");
		Node targetNode2 = folder("deals");

		NodeFieldListImpl list = new NodeFieldListImpl();
		list.add(new NodeFieldListItemImpl(targetNode.getUuid()));
		list.add(new NodeFieldListItemImpl(targetNode2.getUuid()));
		updateNode(FIELD_NAME, list);

		NodeResponse secondResponse = updateNode(FIELD_NAME, new NodeFieldListImpl());
		assertThat(secondResponse.getFields().getNodeFieldList(FIELD_NAME)).as("Updated field list").isNotNull();
		assertThat(secondResponse.getFields().getNodeFieldList(FIELD_NAME).getItems()).as("Field value should be truncated").isEmpty();

	}

	@Test
	public void testCreateNodeWithField() {
		NodeFieldListImpl listField = new NodeFieldListImpl();
		NodeFieldListItemImpl item = new NodeFieldListItemImpl().setUuid(folder("news").getUuid());
		listField.add(item);
		NodeResponse response = createNode(FIELD_NAME, listField);
		NodeFieldList listFromResponse = response.getFields().getNodeFieldList(FIELD_NAME);
		assertEquals(1, listFromResponse.getItems().size());
	}

	@Test
	public void testReadNodeWithExistingField() {
		Node node = folder("2015");

		NodeGraphFieldContainer container = node.getLatestDraftFieldContainer(english());
		NodeGraphFieldList nodeList = container.createNodeList(FIELD_NAME);
		nodeList.createNode("1", folder("news"));
		NodeResponse response = readNode(node);
		NodeFieldList deserializedListField = response.getFields().getNodeFieldList(FIELD_NAME);
		assertNotNull(deserializedListField);
		assertEquals(1, deserializedListField.getItems().size());
	}

	@Test
	public void testReadExpandedNodeListWithExistingField() throws IOException {
		Node newsNode = folder("news");
		Node node = folder("2015");

		// Create node list
		NodeGraphFieldContainer container = node.getLatestDraftFieldContainer(english());
		NodeGraphFieldList nodeList = container.createNodeList(FIELD_NAME);
		nodeList.createNode("1", newsNode);

		// 1. Read node with collapsed fields and check that the collapsed node list item can be read
		NodeResponse responseCollapsed = readNode(node);
		NodeFieldList deserializedNodeListField = responseCollapsed.getFields().getNodeFieldList(FIELD_NAME);
		assertNotNull(deserializedNodeListField);
		assertEquals("The newsNode should be the first item in the list.", newsNode.getUuid(), deserializedNodeListField.getItems().get(0).getUuid());

		// Check whether it is possible to read the field in an expanded form.
		NodeResponse nodeListItem = (NodeResponse) deserializedNodeListField.getItems().get(0);
		assertNotNull(nodeListItem);

		// 2. Read node with expanded fields
		NodeResponse responseExpanded = readNode(node, FIELD_NAME, "bogus");

		// Check collapsed node field
		deserializedNodeListField = responseExpanded.getFields().getNodeFieldList(FIELD_NAME);
		assertNotNull(deserializedNodeListField);
		assertEquals(newsNode.getUuid(), deserializedNodeListField.getItems().get(0).getUuid());

		// Check expanded node field
		NodeFieldListItem deserializedExpandedItem = deserializedNodeListField.getItems().get(0);
		if (deserializedExpandedItem instanceof NodeResponse) {
			NodeResponse expandedField = (NodeResponse) deserializedExpandedItem;
			assertNotNull(expandedField);
			assertEquals(newsNode.getUuid(), expandedField.getUuid());
			assertNotNull(expandedField.getCreator());
		} else {
			fail("The returned item should be a NodeResponse object");
		}
	}
}