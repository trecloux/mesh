package com.gentics.mesh.core.schema;

import static com.gentics.mesh.assertj.MeshAssertions.assertThat;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.core.verticle.eventbus.EventbusAddress.MESH_MIGRATION;
import static com.gentics.mesh.demo.TestDataProvider.PROJECT_NAME;
import static com.gentics.mesh.util.MeshAssert.assertSuccess;
import static com.gentics.mesh.util.MeshAssert.failingLatch;
import static com.gentics.mesh.util.MeshAssert.latchFor;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.CONFLICT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.junit.ComparisonFailure;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.gentics.mesh.FieldUtil;
import com.gentics.mesh.core.data.ContainerType;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Release;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainerVersion;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.error.GenericRestException;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.VersionReference;
import com.gentics.mesh.core.rest.node.field.impl.NumberFieldImpl;
import com.gentics.mesh.core.rest.node.field.impl.StringFieldImpl;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.SchemaReference;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangeModel;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.dagger.MeshInternal;
import com.gentics.mesh.graphdb.NoTx;
import com.gentics.mesh.parameter.impl.VersioningParameters;
import com.gentics.mesh.rest.client.MeshResponse;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.test.performance.TestUtils;

import io.vertx.core.json.JsonObject;

public class SchemaChangesVerticleTest extends AbstractChangesVerticleTest {

	@Test
	public void testUpdateName() throws GenericRestException, Exception {
		try (NoTx noTx = db.noTx()) {
			String name = "new-name";
			SchemaContainer container = schemaContainer("content");
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			Schema request = container.getLatestVersion().getSchema();
			request.setName(name);

			MeshInternal.get().serverSchemaStorage().clear();

			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), request).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "migration_invoked", "content");

			currentVersion.reload();
			container.reload();
			assertEquals("The name of the old version should not be updated", "content", currentVersion.getName());
			assertEquals("The name of the schema was not updated", name, currentVersion.getNextVersion().getName());
			SchemaContainer reloaded = boot.schemaContainerRoot().findByUuid(container.getUuid()).toBlocking().value();
			assertEquals("The name should have been updated", name, reloaded.getName());
		}
	}

	@Test
	public void testBlockingMigrationStatus() throws InterruptedException, IOException {
		try (NoTx noTx = db.noTx()) {
			SchemaContainer container = schemaContainer("content");
			assertNull("The schema should not yet have any changes", container.getLatestVersion().getNextChange());

			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createChangeFieldTypeChange("content", "boolean");

			// Update a single node field in order to trigger a single blocking
			// migration script
			content().getLatestDraftFieldContainer(english()).getHtml("content").setHtml("triggerWait");

			String blockingScript = IOUtils.toString(getClass().getResourceAsStream("/testscripts/longMigrate.js"));
			change.setMigrationScript(blockingScript);
			listOfChanges.getChanges().add(change);

			// Assert migration is in idle
			MeshResponse<GenericMessageResponse> statusFuture = getClient().schemaMigrationStatus().invoke();
			latchFor(statusFuture);
			expectResponseMessage(statusFuture, "migration_status_idle");

			// Trigger migration
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "migration_invoked", "content");

			Schema schema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(schema.getVersion())));

			// Wait a few seconds until the migration has started
			Thread.sleep(3000);

			// Assert migration is running
			statusFuture = getClient().schemaMigrationStatus().invoke();
			latchFor(statusFuture);
			expectResponseMessage(statusFuture, "migration_status_running");
			Thread.sleep(10000);

			// Check for 45 seconds whether the migration finishes
			for (int i = 0; i < 45; i++) {
				try {
					Thread.sleep(1000);
					// Assert migration has finished
					statusFuture = getClient().schemaMigrationStatus().invoke();
					latchFor(statusFuture);
					expectResponseMessage(statusFuture, "migration_status_idle");
					break;
				} catch (ComparisonFailure e) {
					System.out.println("Waiting " + i + " sec");
					if (i == 30) {
						throw e;
					}
				}
			}
		}

	}

	@Test
	public void testUpdateWithConflictingName() {
		try (NoTx noTx = db.noTx()) {
			String name = "folder";
			String originalSchemaName = "content";
			SchemaContainer schema = schemaContainer(originalSchemaName);
			Schema request = schema.getLatestVersion().getSchema();

			MeshInternal.get().serverSchemaStorage().clear();

			// Update name to folder to create a conflict
			request.setName(name);

			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(schema.getUuid(), request).invoke();
			latchFor(future);
			expectException(future, CONFLICT, "schema_conflicting_name", name);
			schema.reload();
			assertEquals("The name of the schema was updated", originalSchemaName, schema.getLatestVersion().getName());
		}
	}

	@Test
	public void testFieldTypeChange() throws Exception {
		try (NoTx noTx = db.noTx()) {
			SchemaContainer container = schemaContainer("content");
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			assertNull("The schema should not yet have any changes", currentVersion.getNextChange());

			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createChangeFieldTypeChange("content", "boolean");
			listOfChanges.getChanges().add(change);

			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());
			// Trigger migration
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "migration_invoked", "content");
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));

			// Wait for event
			failingLatch(latch);
			container.reload();
			container.getLatestVersion().reload();
			currentVersion.reload();
			assertNotNull("The change should have been added to the schema.", currentVersion.getNextChange());
			assertNotNull("The container should now have a new version", currentVersion.getNextVersion());

			// Assert that migration worked
			Node node = content();
			node.reload();
			node.getGraphFieldContainer("en").reload();
			container.reload();
			assertTrue("The version of the original schema and the schema that is now linked to the node should be different.",
					currentVersion.getVersion() != node.getGraphFieldContainer("en").getSchemaContainerVersion().getVersion());
			assertNull("There should no longer be a content field of type html", node.getGraphFieldContainer("en").getHtml("content"));
		}
	}

	@Test
	public void testRemoveAddFieldTypeWithSameKey() throws Exception {
		try (NoTx noTx = db.noTx()) {
			Node content = content();
			content.getLatestDraftFieldContainer(english()).getHtml("content").setHtml("42.1");

			// 1. Create update request by removing the content field from schema and adding a new content with different type
			SchemaContainer container = schemaContainer("content");
			Schema schema = container.getLatestVersion().getSchema();
			schema.removeField("content");
			schema.addField(FieldUtil.createNumberFieldSchema("content"));

			MeshInternal.get().serverSchemaStorage().clear();

			// 3. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// 4. Update the schema server side -> 2.0
			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), schema).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "migration_invoked", schema.getName());
			// 5. assign the new schema version to the release (which will start the migration)
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));
			failingLatch(latch);

			// Add the updated schema to the client store
			schema.setVersion(schema.getVersion() + 1);

			// 6. Read node and check additional field
			NodeResponse response = call(() -> getClient().findNodeByUuid(PROJECT_NAME, content.getUuid(), new VersioningParameters().draft()));
			assertNotNull("The response should contain the content field.", response.getFields().hasField("content"));
			assertEquals("The type of the content field was not changed to a number field.", NumberFieldImpl.class,
					response.getFields().getNumberField("content").getClass());
			assertEquals("2.0", response.getVersion().getNumber());

			// 7. Update the node and set the new field
			NodeUpdateRequest nodeUpdateRequest = new NodeUpdateRequest();
			nodeUpdateRequest.setLanguage("en");
			nodeUpdateRequest.setSchema(new SchemaReference().setName("content"));
			nodeUpdateRequest.getFields().put("content", new NumberFieldImpl().setNumber(42.01));
			nodeUpdateRequest.setVersion(new VersionReference().setNumber("2.0"));
			response = call(() -> getClient().updateNode(PROJECT_NAME, content.getUuid(), nodeUpdateRequest));
			assertNotNull(response);
			assertNotNull(response.getFields().hasField("content"));
			assertEquals(42.01, response.getFields().getNumberField("content").getNumber());
		}
	}

	@Test
	public void testApplyWithEmptyChangesList() {
		try (NoTx noTx = db.noTx()) {
			SchemaContainer container = schemaContainer("content");
			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			expectException(future, BAD_REQUEST, "schema_migration_no_changes_specified");
		}
	}

	@Test
	public void testUnsetSegmentField() throws JsonParseException, JsonMappingException, IOException {
		try (NoTx noTx = db.noTx()) {
			// 1. Create changes
			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createUpdateSchemaChange();
			change.setProperty(SchemaChangeModel.SEGMENT_FIELD_KEY, null);
			listOfChanges.getChanges().add(change);

			// 2. Invoke migration
			SchemaContainer container = schemaContainer("content");
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			assertNull("The schema should not yet have any changes", currentVersion.getNextChange());
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			assertSuccess(future);

			// 3. Assert updated schema
			container.reload();
			currentVersion.reload();
			assertNull("The segment field reference should have been set to null", currentVersion.getNextVersion().getSchema().getSegmentField());
		}
	}

	@Test
	public void testRemoveSegmentField() throws Exception {
		try (NoTx noTx = db.noTx()) {
			Node node = content();
			assertNotNull("The node should have a filename string graph field", node.getGraphFieldContainer("en").getString("filename"));

			// 1. Create changes
			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createRemoveFieldChange("filename");
			listOfChanges.getChanges().add(change);

			// 2. Invoke migration
			SchemaContainer container = schemaContainer("content");
			assertNull("The schema should not yet have any changes", container.getLatestVersion().getNextChange());
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			expectException(future, BAD_REQUEST, "schema_error_segmentfield_invalid", "filename");

			// 3. Assert migrated node
			node.reload();
			NodeGraphFieldContainer fieldContainer = node.getGraphFieldContainer("en");
			fieldContainer.reload();
			assertNull("The node should still have a filename string graph field", fieldContainer.getHtml("filename"));
		}
	}

	@Test
	public void testRemoveField() throws Exception {
		try (NoTx noTx = db.noTx()) {
			// 1. Verify test data
			Node node = content();
			SchemaContainer container = schemaContainer("content");
			assertNotNull("The node should have a html graph field", node.getGraphFieldContainer("en").getHtml("content"));

			// 2. Create changes
			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createRemoveFieldChange("content");
			listOfChanges.getChanges().add(change);

			// 3. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// 4. Invoke migration
			assertNull("The schema should not yet have any changes", container.getLatestVersion().getNextChange());
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			assertSuccess(future);
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));

			// 5. Wait for migration to finish
			failingLatch(latch);
			container.reload();
			currentVersion.reload();
			assertNotNull("The change should have been added to the schema.", currentVersion.getNextChange());

			// 6. Assert migrated node
			node.reload();
			NodeGraphFieldContainer fieldContainer = node.getGraphFieldContainer("en");
			fieldContainer.reload();
			assertNull("The node should no longer have a content html graph field", fieldContainer.getHtml("content"));
		}
	}

	@Test
	public void testAddField() throws Exception {

		try (NoTx noTx = db.noTx()) {
			// 1. Setup changes
			SchemaContainer container = schemaContainer("content");
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			assertNull("The schema should not yet have any changes", currentVersion.getNextChange());
			SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
			SchemaChangeModel change = SchemaChangeModel.createAddFieldChange("newField", "html", "label1234");
			listOfChanges.getChanges().add(change);

			// 2. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// 3. Invoke migration
			MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "migration_invoked", "content");
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));

			// 4. Latch for completion
			failingLatch(latch);
			container.reload();
			container.getLatestVersion().reload();
			currentVersion.reload();
			assertNotNull("The change should have been added to the schema.", currentVersion.getNextChange());
			assertNotEquals("The container should now have a new version", currentVersion.getUuid(), container.getLatestVersion().getUuid());

			// Assert that migration worked
			Node node = content();
			node.reload();
			assertNotNull("The schema of the node should contain the new field schema",
					node.getGraphFieldContainer("en").getSchemaContainerVersion().getSchema().getField("newField"));
			assertTrue("The version of the original schema and the schema that is now linked to the node should be different.",
					currentVersion.getVersion() != node.getGraphFieldContainer("en").getSchemaContainerVersion().getVersion());
			assertEquals("label1234", node.getGraphFieldContainer("en").getSchemaContainerVersion().getSchema().getField("newField").getLabel());

		}
	}

	/**
	 * Construct a latch which will release when the migration has finished.
	 * 
	 * @return
	 */
	public static CyclicBarrier waitForMigration(MeshRestClient client) {
		// Construct latch in order to wait until the migration completed event
		// was received
		CyclicBarrier barrier = new CyclicBarrier(2);
		client.eventbus(ws -> {
			// Register to migration events
			JsonObject msg = new JsonObject().put("type", "register").put("address", MESH_MIGRATION.toString());
			ws.writeFinalTextFrame(msg.encode());

			// Handle migration events
			ws.handler(buff -> {
				String str = buff.toString();
				JsonObject received = new JsonObject(str);
				JsonObject rec = received.getJsonObject("body");
				if ("completed".equalsIgnoreCase(rec.getString("type"))) {
					try {
						barrier.await(10, TimeUnit.SECONDS);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}
			});

		});
		return barrier;
	}

	@Test
	public void testUpdateMultipleTimes() throws Exception {
		try (NoTx noTx = db.noTx()) {
			// Assert start condition
			SchemaContainer container = schemaContainer("content");
			SchemaContainerVersion currentVersion = container.getLatestVersion();
			assertNull("The schema should not yet have any changes", currentVersion.getNextChange());

			// 2. Setup eventbus bridged latch
			CyclicBarrier barrier = waitForMigration(getClient());

			for (int i = 0; i < 10; i++) {

				// 1. Setup changes
				SchemaChangesListModel listOfChanges = new SchemaChangesListModel();
				SchemaChangeModel change = SchemaChangeModel.createAddFieldChange("newField_" + i, "html", null);
				listOfChanges.getChanges().add(change);

				// 3. Invoke migration
				MeshResponse<GenericMessageResponse> future = getClient().applyChangesToSchema(container.getUuid(), listOfChanges).invoke();
				latchFor(future);
				assertSuccess(future);
				expectResponseMessage(future, "migration_invoked", "content");
				Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
				call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
						new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));

				// 4. Latch for completion
				barrier.await(10, TimeUnit.SECONDS);
				container.reload();
				container.getLatestVersion().reload();
				currentVersion.reload();
				assertNotNull("The change should have been added to the schema.", currentVersion.getNextChange());
				assertNotEquals("The container should now have a new version", currentVersion.getUuid(), container.getLatestVersion().getUuid());

				// Assert that migration worked
				Node node = content();
				node.reload();
				node.getGraphFieldContainer("en").reload();
				node.getGraphFieldContainer("en").getSchemaContainerVersion().reload();

				assertNotNull("The schema of the node should contain the new field schema",
						node.getGraphFieldContainer("en").getSchemaContainerVersion().getSchema().getField("newField_" + i));
				assertTrue("The version of the original schema and the schema that is now linked to the node should be different.",
						currentVersion.getVersion() != node.getGraphFieldContainer("en").getSchemaContainerVersion().getVersion());

			}

			// Validate schema changes and versions
			container.reload();
			assertEquals("We invoked 10 migration. Thus we expect 11 versions.", 11, container.findAll().size());
			assertNull("The last version should not have any changes", container.getLatestVersion().getNextChange());
			assertNull("The last version should not have any futher versions", container.getLatestVersion().getNextVersion());

			SchemaContainerVersion version = container.getLatestVersion();
			int nVersions = 0;
			while (true) {
				version = version.getPreviousVersion();
				if (version == null) {
					break;
				}
				version.reload();
				assertNotNull("The schema version {" + version.getUuid() + "-" + version.getVersion() + "} should have a next change",
						version.getNextChange());
				assertEquals("The version is not referencing the correct parent container.", container.getUuid(),
						version.getSchemaContainer().getUuid());
				nVersions++;
			}

			assertEquals("The latest version should have exactly 10 previous versions.", nVersions, 10);
			assertTrue("The user should still have update permissions on the schema", user().hasPermission(container, UPDATE_PERM));
		}
	}

	/**
	 * Update the schema without applying any changes.
	 */
	@Test
	public void testNoChangesUpdate() {
		try (NoTx noTx = db.noTx()) {
			SchemaContainer container = schemaContainer("content");
			Schema schema = container.getLatestVersion().getSchema();

			// Update the schema server side
			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), schema).invoke();
			latchFor(future);
			assertSuccess(future);
			expectResponseMessage(future, "schema_update_no_difference_detected");
		}
	}

	@Test
	public void testUpdateAddField() throws Exception {
		try (NoTx noTx = db.noTx()) {
			// 1. Setup schema
			Node content = content();
			SchemaContainer container = schemaContainer("content");
			Schema schema = container.getLatestVersion().getSchema();
			assertEquals("The segment field name should be set", "filename", schema.getSegmentField());
			schema.getFields().add(FieldUtil.createStringFieldSchema("extraname").setLabel("someLabel"));
			MeshInternal.get().serverSchemaStorage().clear();

			// 2. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// 3. Update the schema server side -> 2.0
			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), schema).invoke();
			latchFor(future);
			assertSuccess(future);
			// 4. assign the new schema version to the release
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));
			failingLatch(latch);

			MeshResponse<Schema> schemaFuture = getClient().findSchemaByUuid(container.getUuid()).invoke();
			latchFor(schemaFuture);
			assertSuccess(schemaFuture);
			assertEquals("The segment field name should be set", "filename", schemaFuture.result().getSegmentField());
			assertEquals("someLabel", schemaFuture.result().getField("extraname").getLabel());

			schema.setVersion(schema.getVersion() + 1);

			// Read node and check additional field
			NodeResponse response = call(() -> getClient().findNodeByUuid(PROJECT_NAME, content.getUuid(), new VersioningParameters().draft()));
			assertNotNull(response);

			// Update the node and set the new field
			NodeUpdateRequest nodeUpdateRequest = new NodeUpdateRequest();
			nodeUpdateRequest.setLanguage("en");
			nodeUpdateRequest.setVersion(new VersionReference().setNumber("2.0"));
			nodeUpdateRequest.setSchema(new SchemaReference().setName("content"));
			nodeUpdateRequest.getFields().put("extraname", new StringFieldImpl().setString("sometext"));
			response = call(() -> getClient().updateNode(PROJECT_NAME, content.getUuid(), nodeUpdateRequest));
			assertNotNull(response);
			assertNotNull(response.getFields().getStringField("extraname"));
			assertEquals("sometext", response.getFields().getStringField("extraname").getString());

			// Read node and check additional field
			response = call(() -> getClient().findNodeByUuid(PROJECT_NAME, content.getUuid(), new VersioningParameters().draft()));
			assertNotNull(response);
			assertNotNull(response.getFields().hasField("extraname"));
		}
	}

	@Test
	public void testRemoveField2() throws Exception {
		try (NoTx noTx = db.noTx()) {
			Node content = content();

			SchemaContainer container = schemaContainer("content");
			Schema schema = container.getLatestVersion().getSchema();
			schema.removeField("content");

			// Update the schema client side
			MeshInternal.get().serverSchemaStorage().clear();

			// 2. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// Update the schema server side
			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), schema).invoke();
			latchFor(future);
			assertSuccess(future);
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, project().getLatestRelease().getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));

			schema.setVersion(schema.getVersion() + 1);

			failingLatch(latch);

			// Read node and check additional field
			NodeResponse response = call(() -> getClient().findNodeByUuid(PROJECT_NAME, content.getUuid(), new VersioningParameters().draft()));
			assertNotNull(response);
			assertNull(response.getFields().getStringField("content"));
		}
	}

	@Test
	public void testMigrationForRelease() throws Exception {
		try (NoTx noTx = db.noTx()) {
			Release initialRelease = project().getLatestRelease();
			Release newRelease = project().getReleaseRoot().create("newrelease", user());

			Node content = content();
			content.createGraphFieldContainer(english(), newRelease, user());

			SchemaContainer container = schemaContainer("content");
			Schema schema = container.getLatestVersion().getSchema();
			schema.getFields().add(FieldUtil.createStringFieldSchema("extraname"));
			MeshInternal.get().serverSchemaStorage().clear();

			// 2. Setup eventbus bridged latch
			CountDownLatch latch = TestUtils.latchForMigrationCompleted(getClient());

			// 3. Update the schema server side
			MeshResponse<GenericMessageResponse> future = getClient().updateSchema(container.getUuid(), schema).invoke();
			latchFor(future);
			assertSuccess(future);
			// 4. assign the new schema version to the initial release
			Schema updatedSchema = call(() -> getClient().findSchemaByUuid(container.getUuid()));
			call(() -> getClient().assignReleaseSchemaVersions(PROJECT_NAME, initialRelease.getUuid(),
					new SchemaReference().setName("content").setVersion(updatedSchema.getVersion())));
			failingLatch(latch);

			// node must be migrated for initial release
			content.reload();
			container.reload();
			assertThat(content.getGraphFieldContainer("en", initialRelease.getUuid(), ContainerType.DRAFT)).isOf(container.getLatestVersion());

			// node must not be migrated for new release
			assertThat(content.getGraphFieldContainer("en", newRelease.getUuid(), ContainerType.DRAFT))
					.isOf(container.getLatestVersion().getPreviousVersion());
		}
	}
}
