package com.gentics.mesh.test.context;

import static com.gentics.mesh.mock.Mocks.getMockedInternalActionContext;
import static com.gentics.mesh.mock.Mocks.getMockedRoutingContext;
import static com.gentics.mesh.test.ClientHelper.call;
import static com.gentics.mesh.test.TestDataProvider.PROJECT_NAME;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import com.gentics.mesh.FieldUtil;
import com.gentics.mesh.Mesh;
import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Group;
import com.gentics.mesh.core.data.Language;
import com.gentics.mesh.core.data.MeshAuthUser;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.Release;
import com.gentics.mesh.core.data.Role;
import com.gentics.mesh.core.data.Tag;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.impl.MeshAuthUserImpl;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.root.MeshRoot;
import com.gentics.mesh.core.data.schema.MicroschemaContainer;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.search.SearchQueueBatch;
import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.group.GroupCreateRequest;
import com.gentics.mesh.core.rest.group.GroupResponse;
import com.gentics.mesh.core.rest.group.GroupUpdateRequest;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaCreateRequest;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaResponse;
import com.gentics.mesh.core.rest.microschema.impl.MicroschemaUpdateRequest;
import com.gentics.mesh.core.rest.node.NodeCreateRequest;
import com.gentics.mesh.core.rest.node.NodeResponse;
import com.gentics.mesh.core.rest.node.NodeUpdateRequest;
import com.gentics.mesh.core.rest.node.field.Field;
import com.gentics.mesh.core.rest.project.ProjectCreateRequest;
import com.gentics.mesh.core.rest.project.ProjectResponse;
import com.gentics.mesh.core.rest.project.ProjectUpdateRequest;
import com.gentics.mesh.core.rest.role.RoleCreateRequest;
import com.gentics.mesh.core.rest.role.RoleResponse;
import com.gentics.mesh.core.rest.role.RoleUpdateRequest;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.SchemaModel;
import com.gentics.mesh.core.rest.schema.impl.BinaryFieldSchemaImpl;
import com.gentics.mesh.core.rest.schema.impl.SchemaCreateRequest;
import com.gentics.mesh.core.rest.schema.impl.SchemaReferenceImpl;
import com.gentics.mesh.core.rest.schema.impl.SchemaResponse;
import com.gentics.mesh.core.rest.schema.impl.SchemaUpdateRequest;
import com.gentics.mesh.core.rest.tag.TagCreateRequest;
import com.gentics.mesh.core.rest.tag.TagFamilyCreateRequest;
import com.gentics.mesh.core.rest.tag.TagFamilyResponse;
import com.gentics.mesh.core.rest.tag.TagFamilyUpdateRequest;
import com.gentics.mesh.core.rest.tag.TagResponse;
import com.gentics.mesh.core.rest.tag.TagUpdateRequest;
import com.gentics.mesh.core.rest.user.NodeReference;
import com.gentics.mesh.core.rest.user.UserCreateRequest;
import com.gentics.mesh.core.rest.user.UserResponse;
import com.gentics.mesh.core.rest.user.UserUpdateRequest;
import com.gentics.mesh.dagger.MeshComponent;
import com.gentics.mesh.dagger.MeshInternal;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.parameter.LinkType;
import com.gentics.mesh.parameter.SchemaUpdateParameters;
import com.gentics.mesh.parameter.impl.NodeParametersImpl;
import com.gentics.mesh.parameter.impl.VersioningParametersImpl;
import com.gentics.mesh.rest.client.MeshRequest;
import com.gentics.mesh.rest.client.MeshRestClient;
import com.gentics.mesh.search.TrackingSearchProvider;
import com.gentics.mesh.search.SearchProvider;
import com.gentics.mesh.test.TestDataProvider;
import com.gentics.mesh.util.VersionNumber;
import com.syncleus.ferma.tx.Tx;
import com.syncleus.ferma.tx.TxAction;
import com.syncleus.ferma.tx.TxAction0;
import com.syncleus.ferma.tx.TxAction1;
import com.syncleus.ferma.tx.TxAction2;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.RoutingContext;
import io.vertx.test.core.TestUtils;

public interface TestHelperMethods {

	MeshTestContext getTestContext();

	default Database db() {
		return MeshInternal.get().database();
	}

	/**
	 * Create a new transaction.
	 * 
	 * @see Database#tx()
	 * @return
	 */
	default Tx tx() {
		return db().tx();
	}

	default void tx(TxAction0 handler) {
		db().tx(handler);
	}

	default <T> T tx(TxAction1<T> handler) {
		return db().tx(handler);
	}

	default void tx(TxAction2 handler) {
		db().tx(handler);
	}

	default <T> T tx(TxAction<T> handler) {
		return db().tx(handler);
	}

	default BootstrapInitializer boot() {
		return MeshInternal.get().boot();
	}

	default TestDataProvider data() {
		return getTestContext().getData();
	}

	default Role role() {
		return data().role();
	}

	default MeshAuthUser getRequestUser() {
		return data().getUserInfo().getUser().reframe(MeshAuthUserImpl.class);
	}

	default User user() {
		return data().user();
	}

	default Role anonymousRole() {
		return data().getAnonymousRole();
	}

	default MeshRoot meshRoot() {
		return data().getMeshRoot();
	}

	default Group group() {
		return data().getUserInfo().getGroup();
	}

	default String groupUuid() {
		return data().getUserInfo().getGroupUuid();
	}

	default String userUuid() {
		return data().getUserInfo().getUserUuid();
	}

	/**
	 * Return the uuid of the initial project release.
	 * 
	 * @return
	 */
	default String initialReleaseUuid() {
		return data().releaseUuid();
	}

	default String roleUuid() {
		return data().getUserInfo().getRoleUuid();
	}

	default String projectUuid() {
		return data().projectUuid();
	}

	default String contentUuid() {
		return data().getContentUuid();
	}

	default MeshRestClient client() {
		return getTestContext().getClient();
	}

	default TrackingSearchProvider trackingSearchProvider() {
		return getTestContext().getTrackingSearchProvider();
	}

	/**
	 * Return the test project.
	 * 
	 * @return
	 */
	default Project project() {
		return data().getProject();
	}

	/**
	 * Return the http port used by the mesh http server.
	 * 
	 * @return
	 */
	default int port() {
		return getTestContext().getPort();
	}

	default Node folder(String key) {
		return data().getFolder(key);
	}

	default Node content(String key) {
		return data().getContent(key);
	}

	default TagFamily tagFamily(String key) {
		return data().getTagFamily(key);
	}

	default Tag tag(String key) {
		return data().getTag(key);
	}

	default SchemaContainer schemaContainer(String key) {
		return data().getSchemaContainer(key);
	}

	default Map<String, SchemaContainer> schemaContainers() {
		return data().getSchemaContainers();
	}

	default Map<String, Role> roles() {
		return data().getRoles();
	}

	default Map<String, ? extends Tag> tags() {
		return data().getTags();
	}

	default Language english() {
		return data().getEnglish();
	}

	default Language german() {
		return data().getGerman();
	}

	default Map<String, Group> groups() {
		return data().getGroups();
	}

	default Map<String, MicroschemaContainer> microschemaContainers() {
		return data().getMicroschemaContainers();
	}

	default MicroschemaContainer microschemaContainer(String key) {
		return data().getMicroschemaContainers().get(key);
	}

	default RoutingContext mockRoutingContext() {
		return getMockedRoutingContext("", false, user(), project());
	}

	default RoutingContext mockRoutingContext(String query) {
		return getMockedRoutingContext(query, false, user(), project());
	}

	default InternalActionContext mockActionContext() {
		return getMockedInternalActionContext("", user(), project());
	}

	default InternalActionContext mockActionContext(String query) {
		return getMockedInternalActionContext(query, user(), project());
	}

	/**
	 * Returns the news overview node which has no tags.
	 * 
	 * @return
	 */
	default Node content() {
		return data().getContent("news overview");
	}

	/**
	 * Return the latest release of the dummy project.
	 * 
	 * @return
	 */
	default Release latestRelease() {
		return project().getLatestRelease();
	}

	/**
	 * Returns the initial release of the dummy project.
	 * 
	 * @return
	 */
	default Release initialRelease() {
		return project().getInitialRelease();
	}

	default UserResponse readUser(String uuid) {
		return call(() -> client().findUserByUuid(uuid));
	}

	default UserResponse updateUser(String uuid, String newUserName) {
		UserUpdateRequest userUpdateRequest = new UserUpdateRequest();
		userUpdateRequest.setUsername(newUserName);
		return call(() -> client().updateUser(uuid, userUpdateRequest));
	}

	default void deleteUser(String uuid) {
		call(() -> client().deleteUser(uuid));
	}

	default GroupResponse createGroup(String groupName) {
		GroupCreateRequest request = new GroupCreateRequest();
		request.setName(groupName);
		return call(() -> client().createGroup(request));
	}

	default GroupResponse readGroup(String uuid) {
		return call(() -> client().findGroupByUuid(uuid));
	}

	default GroupResponse updateGroup(String uuid, String newGroupName) {
		GroupUpdateRequest groupUpdateRequest = new GroupUpdateRequest();
		groupUpdateRequest.setName(newGroupName);
		return call(() -> client().updateGroup(uuid, groupUpdateRequest));
	}

	default void deleteGroup(String uuid) {
		call(() -> client().deleteGroup(uuid));
	}

	default RoleResponse createRole(String roleName, String groupUuid) {
		RoleCreateRequest roleCreateRequest = new RoleCreateRequest();
		roleCreateRequest.setName(roleName);
		return call(() -> client().createRole(roleCreateRequest));
	}

	default RoleResponse readRole(String uuid) {
		return call(() -> client().findRoleByUuid(uuid));
	}

	default void deleteRole(String uuid) {
		call(() -> client().deleteRole(uuid));
	}

	default RoleResponse updateRole(String uuid, String newRoleName) {
		RoleUpdateRequest request = new RoleUpdateRequest();
		request.setName(newRoleName);
		return call(() -> client().updateRole(uuid, request));
	}

	default TagResponse createTag(String projectName, String tagFamilyUuid, String tagName) {
		TagCreateRequest tagCreateRequest = new TagCreateRequest();
		tagCreateRequest.setName(tagName);
		return call(() -> client().createTag(projectName, tagFamilyUuid, tagCreateRequest));
	}

	default TagResponse readTag(String projectName, String tagFamilyUuid, String uuid) {
		return call(() -> client().findTagByUuid(projectName, tagFamilyUuid, uuid));
	}

	default TagResponse updateTag(String projectName, String tagFamilyUuid, String uuid, String newTagName) {
		TagUpdateRequest tagUpdateRequest = new TagUpdateRequest();
		tagUpdateRequest.setName(newTagName);
		return call(() -> client().updateTag(projectName, tagFamilyUuid, uuid, tagUpdateRequest));
	}

	default MeshRequest<NodeResponse> createNodeAsync(String fieldKey, Field field) {
		String parentNodeUuid = tx(() -> folder("2015").getUuid());
		NodeCreateRequest nodeCreateRequest = new NodeCreateRequest();
		nodeCreateRequest.setParentNode(new NodeReference().setUuid(parentNodeUuid));
		nodeCreateRequest.setSchema(new SchemaReferenceImpl().setName("folder"));
		nodeCreateRequest.setLanguage("en");
		if (fieldKey != null) {
			nodeCreateRequest.getFields().put(fieldKey, field);
		}
		return client().createNode(PROJECT_NAME, nodeCreateRequest, new NodeParametersImpl().setLanguages("en"));
	}

	default NodeResponse createNode(String fieldKey, Field field) {
		NodeResponse response = call(() -> createNodeAsync(fieldKey, field));
		assertNotNull("The response could not be found in the result of the future.", response);
		if (fieldKey != null) {
			assertNotNull("The field was not included in the response.", response.getFields().hasField(fieldKey));
		}
		return response;
	}

	default NodeResponse readNode(String projectName, String uuid) {
		return call(() -> client().findNodeByUuid(projectName, uuid, new VersioningParametersImpl().draft()));
	}

	default void deleteNode(String projectName, String uuid) {
		call(() -> client().deleteNode(projectName, uuid));
	}

	default NodeResponse updateNode(String projectName, String uuid, String nameFieldValue) {
		NodeUpdateRequest nodeUpdateRequest = new NodeUpdateRequest();
		return call(() -> client().updateNode(projectName, uuid, nodeUpdateRequest));
	}

	default TagFamilyResponse createTagFamily(String projectName, String tagFamilyName) {
		TagFamilyCreateRequest tagFamilyCreateRequest = new TagFamilyCreateRequest();
		tagFamilyCreateRequest.setName(tagFamilyName);
		return call(() -> client().createTagFamily(projectName, tagFamilyCreateRequest));
	}

	default TagFamilyResponse readTagFamily(String projectName, String uuid) {
		return call(() -> client().findTagFamilyByUuid(projectName, uuid));
	}

	default TagFamilyResponse updateTagFamily(String projectName, String uuid, String newTagFamilyName) {
		TagFamilyUpdateRequest tagFamilyUpdateRequest = new TagFamilyUpdateRequest();
		tagFamilyUpdateRequest.setName(newTagFamilyName);
		return call(() -> client().updateTagFamily(projectName, uuid, tagFamilyUpdateRequest));
	}

	default void deleteTagFamily(String projectName, String uuid) {
		call(() -> client().deleteTagFamily(projectName, uuid));
	}

	/**
	 * Migrate the node from one release to another
	 * 
	 * @param projectName
	 *            project name
	 * @param uuid
	 *            node Uuid
	 * @param sourceReleaseName
	 *            source release name
	 * @param targetReleaseName
	 *            target release name
	 * @return migrated node
	 */
	default NodeResponse migrateNode(String projectName, String uuid, String sourceReleaseName, String targetReleaseName) {
		// read node from source release
		NodeResponse nodeResponse = call(() -> client().findNodeByUuid(projectName, uuid, new VersioningParametersImpl().setRelease(sourceReleaseName)
				.draft()));

		Schema schema = schemaContainer(nodeResponse.getSchema().getName()).getLatestVersion().getSchema();

		// update node for target release
		NodeUpdateRequest update = new NodeUpdateRequest();
		update.setLanguage(nodeResponse.getLanguage());

		nodeResponse.getFields().keySet().forEach(key -> update.getFields().put(key, nodeResponse.getFields().getField(key, schema.getField(key))));
		return call(() -> client().updateNode(projectName, uuid, update, new VersioningParametersImpl().setRelease(targetReleaseName)));
	}

	default ProjectResponse createProject(String projectName) {
		ProjectCreateRequest projectCreateRequest = new ProjectCreateRequest();
		projectCreateRequest.setName(projectName);
		projectCreateRequest.setSchema(new SchemaReferenceImpl().setName("folder"));
		return call(() -> client().createProject(projectCreateRequest));
	}

	default ProjectResponse readProject(String uuid) {
		return call(() -> client().findProjectByUuid(uuid));
	}

	default ProjectResponse updateProject(String uuid, String projectName) {
		ProjectUpdateRequest projectUpdateRequest = new ProjectUpdateRequest();
		projectUpdateRequest.setName(projectName);
		return call(() -> client().updateProject(uuid, projectUpdateRequest));
	}

	default void deleteProject(String uuid) {
		call(() -> client().deleteProject(uuid));
	}

	default SchemaResponse createSchema(String schemaName) {
		SchemaCreateRequest schema = FieldUtil.createSchemaCreateRequest();
		schema.setName(schemaName);
		return call(() -> client().createSchema(schema));
	}

	default Schema readSchema(String uuid) {
		return call(() -> client().findSchemaByUuid(uuid));
	}

	default GenericMessageResponse updateSchema(String uuid, String schemaName, SchemaUpdateParameters... updateParameters) {
		SchemaUpdateRequest schema = new SchemaUpdateRequest();
		schema.setName(schemaName);
		return call(() -> client().updateSchema(uuid, schema, updateParameters));
	}

	default void deleteSchema(String uuid) {
		call(() -> client().deleteSchema(uuid));
	}

	default MicroschemaResponse createMicroschema(String microschemaName) {
		MicroschemaCreateRequest microschema = new MicroschemaCreateRequest();
		microschema.setName(microschemaName);
		return call(() -> client().createMicroschema(microschema));
	}

	default GenericMessageResponse updateMicroschema(String uuid, String microschemaName, SchemaUpdateParameters... parameters) {
		MicroschemaUpdateRequest microschema = FieldUtil.createMinimalValidMicroschemaUpdateRequest();
		microschema.setName(microschemaName);
		return call(() -> client().updateMicroschema(uuid, microschema, parameters));
	}

	/**
	 * Prepare the schema of the given node by adding the binary content field to its schema fields. This method will also update the clientside schema storage.
	 * 
	 * @param node
	 * @param mimeTypeWhitelist
	 * @param binaryFieldName
	 * @throws IOException
	 */
	default void prepareSchema(Node node, String mimeTypeWhitelist, String binaryFieldName) throws IOException {
		// Update the schema and enable binary support for folders
		SchemaModel schema = node.getSchemaContainer().getLatestVersion().getSchema();
		schema.addField(new BinaryFieldSchemaImpl().setAllowedMimeTypes(mimeTypeWhitelist).setName(binaryFieldName).setLabel("Binary content"));
		node.getSchemaContainer().getLatestVersion().setSchema(schema);
		MeshInternal.get().serverSchemaStorage().clear();
		// node.getSchemaContainer().setSchema(schema);
	}

	default MeshRequest<NodeResponse> uploadRandomData(Node node, String languageTag, String fieldKey, int binaryLen, String contentType,
                                                       String fileName) {

		VersionNumber version = tx(() -> node.getGraphFieldContainer("en").getVersion());
		String uuid = tx(node::getUuid);

		Buffer buffer = TestUtils.randomBuffer(binaryLen);
		return client().updateNodeBinaryField(PROJECT_NAME, uuid, languageTag, version.toString(), fieldKey, buffer, fileName, contentType,
				new NodeParametersImpl().setResolveLinks(LinkType.FULL));
	}

	default NodeResponse uploadImage(Node node, String languageTag, String fieldName) throws IOException {
		String contentType = "image/jpeg";
		String fileName = "blume.jpg";
		try (Tx tx = tx()) {
			prepareSchema(node, "image/.*", fieldName);
			tx.success();
		}

		InputStream ins = getClass().getResourceAsStream("/pictures/blume.jpg");
		byte[] bytes = IOUtils.toByteArray(ins);
		Buffer buffer = Buffer.buffer(bytes);
		VersionNumber version = node.getGraphFieldContainer(languageTag).getVersion();

		return call(() -> client().updateNodeBinaryField(PROJECT_NAME, node.getUuid(), languageTag, version.toString(), fieldName, buffer, fileName,
				contentType));
	}

	default UserResponse createUser(String username) {
		UserCreateRequest request = new UserCreateRequest();
		request.setUsername(username);
		request.setPassword("test1234");
		request.setGroupUuid(groupUuid());

		return call(() -> client().createUser(request));
	}

	default MeshComponent meshDagger() {
		return MeshInternal.get();
	}

	default SearchProvider searchProvider() {
		return meshDagger().searchProvider();
	}

	default int getNodeCount() {
		return data().getNodeCount();
	}

	default HttpClient createHttpClient() {
		HttpClientOptions options = new HttpClientOptions();
		options.setDefaultHost("localhost");
		options.setDefaultPort(port());
		HttpClient client = Mesh.vertx().createHttpClient(options);
		return client;
	}

	default SchemaContainer getSchemaContainer() {
		SchemaContainer container = data().getSchemaContainer("content");
		return container;
	}

	default Vertx vertx() {
		return getTestContext().getVertx();
	}

	default SearchQueueBatch createBatch() {
		return MeshInternal.get().searchQueue().create();
	}

	default Map<String, User> users() {
		return data().getUsers();
	}

	default void disableAnonymousAccess() {
		Mesh.mesh().getOptions().getAuthenticationOptions().setEnableAnonymousAccess(false);
	}

}
