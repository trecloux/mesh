package com.gentics.mesh.graphql.type;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PUBLISHED_PERM;
import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.gentics.mesh.core.data.ContainerType;
import com.gentics.mesh.core.data.EditorTrackingVertex;
import com.gentics.mesh.core.data.NodeGraphFieldContainer;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.User;
import com.gentics.mesh.core.data.node.Node;
import com.gentics.mesh.core.data.page.Page;
import com.gentics.mesh.error.MeshConfigurationException;
import com.gentics.mesh.graphql.context.GraphQLContext;
import com.gentics.mesh.parameter.PagingParameters;
import com.gentics.mesh.search.index.node.NodeIndexHandler;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLObjectType.Builder;
import graphql.schema.GraphQLTypeReference;

/**
 * Type provider for the content type. Internally this will map to {@link NodeGraphFieldContainer} vertices.
 */
@Singleton
public class ContentTypeProvider extends AbstractTypeProvider {

	@Inject
	public NodeFieldTypeProvider nodeFieldTypeProvider;

	@Inject
	public NodeIndexHandler nodeIndexHandler;

	@Inject
	public ContentTypeProvider() {
	}

	public Object parentNodeFetcher(DataFetchingEnvironment env) {
		GraphQLContext gc = env.getContext();
		NodeGraphFieldContainer container = env.getSource();
		Node node = container.getParentNode();
		return gc.requiresPerm(node, READ_PERM, READ_PUBLISHED_PERM);
	}

	public Object editorFetcher(DataFetchingEnvironment env) {
		GraphQLContext gc = env.getContext();
		EditorTrackingVertex vertex = env.getSource();
		User user = vertex.getEditor();
		return gc.requiresPerm(user, READ_PERM);
	}

	public GraphQLObjectType createContentType(Project project) {

		Builder type = newObject().name("Content")
				.description("Language specific node content which contains the node fields");

		// .uuid
		//TODO decide whether we want to return the node uuid here

		// .node
		type.field(newFieldDefinition().name("node")
				.description("Node which the content belongs to.")
				.type(new GraphQLTypeReference("Node"))
				.dataFetcher(this::parentNodeFetcher));

		// .path
		type.field(newFieldDefinition().name("path")
				.description("Webroot path of the content.")
				.type(GraphQLString)
				.dataFetcher(env -> {
					GraphQLContext gc = env.getContext();
					NodeGraphFieldContainer container = env.getSource();
					ContainerType containerType = ContainerType.forVersion(gc.getVersioningParameters().getVersion());
					String releaseUuid = gc.getRelease().getUuid();
					String languageTag = container.getLanguage().getLanguageTag();
					return container.getParentNode().getPath(releaseUuid, containerType, languageTag);
				}));

		// .edited
		type.field(newFieldDefinition().name("edited")
				.description("ISO8601 formatted edit timestamp.")
				.type(GraphQLString)
				.dataFetcher(env -> {
					EditorTrackingVertex vertex = env.getSource();
					return vertex.getLastEditedDate();
				}));

		// .editor
		type.field(newFieldDefinition().name("editor")
				.description("Editor of the element")
				.type(new GraphQLTypeReference("User"))
				.dataFetcher(this::editorFetcher));

		// .isPublished
		type.field(newFieldDefinition().name("isPublished")
				.description("Check whether the content is published.")
				.type(GraphQLBoolean)
				.dataFetcher(env -> {
					GraphQLContext gc = env.getContext();
					NodeGraphFieldContainer container = env.getSource();
					return container.isPublished(gc.getRelease()
							.getUuid());
				}));

		// .isDraft
		type.field(newFieldDefinition().name("isDraft")
				.description("Check whether the content is a draft.")
				.type(GraphQLBoolean)
				.dataFetcher(env -> {
					GraphQLContext gc = env.getContext();
					NodeGraphFieldContainer container = env.getSource();
					return container.isDraft(gc.getRelease()
							.getUuid());
				}));

		// .version
		type.field(newFieldDefinition().name("version")
				.description("Version of the content.")
				.type(GraphQLString)
				.dataFetcher(env -> {
					NodeGraphFieldContainer container = env.getSource();
					return container.getVersion()
							.getFullVersion();
				}));

		// .fields
		type.field(newFieldDefinition().name("fields")
				.description("Contains the fields of the content.")
				.type(nodeFieldTypeProvider.getSchemaFieldsType(project))
				.dataFetcher(env -> {
					// The fields can be accessed via the container so we can directly pass it along.
					NodeGraphFieldContainer container = env.getSource();
					return container;
				}));

		// .language
		type.field(newFieldDefinition().name("language")
				.description("The language of this content.")
				.type(GraphQLString)
				.dataFetcher(env -> {
					NodeGraphFieldContainer container = env.getSource();
					return container.getLanguage()
							.getLanguageTag();
				}));

		return type.build();
	}

	/**
	 * Invoke a elasticsearch using the provided query and return a page of found containers.
	 * 
	 * @param gc
	 * @param query
	 * @param pagingInfo
	 * @return
	 */
	public Page<? extends NodeGraphFieldContainer> handleContentSearch(GraphQLContext gc, String query, PagingParameters pagingInfo) {
		try {
			return nodeIndexHandler.handleContainerSearch(gc, query, pagingInfo, READ_PERM, READ_PUBLISHED_PERM);
		} catch (MeshConfigurationException | InterruptedException | ExecutionException | TimeoutException e) {
			throw new RuntimeException(e);
		}
	}
}
