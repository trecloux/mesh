package com.gentics.mesh.search.index;

import static com.gentics.mesh.search.index.MappingHelper.NAME_KEY;
import static com.gentics.mesh.search.index.MappingHelper.NOT_ANALYZED;
import static com.gentics.mesh.search.index.MappingHelper.STRING;
import static com.gentics.mesh.search.index.MappingHelper.fieldType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.TagFamily;
import com.gentics.mesh.core.data.root.ProjectRoot;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.search.SearchQueueEntry;

import io.vertx.core.json.JsonObject;

@Component
public class TagFamilyIndexHandler extends AbstractIndexHandler<TagFamily> {

	/**
	 * Name of the custom property of SearchQueueEntry containing the project UUID.
	 */
	public final static String CUSTOM_PROJECT_UUID = "projectUuid";

	private static TagFamilyIndexHandler instance;

	@PostConstruct
	public void setup() {
		instance = this;
	}

	public static TagFamilyIndexHandler getInstance() {
		return instance;
	}

	@Override
	protected String getIndex(SearchQueueEntry entry) {
		return getIndexName(entry.getCustomProperty(CUSTOM_PROJECT_UUID));
	}

	@Override
	public Set<String> getIndices() {
		return db.noTrx(() -> {
			ProjectRoot root = BootstrapInitializer.getBoot().meshRoot().getProjectRoot();
			root.reload();
			List<? extends Project> projects = root.findAll();
			return projects.stream().map(project -> getIndexName(project.getUuid())).collect(Collectors.toSet());
		});
	}

	@Override
	public Set<String> getAffectedIndices(InternalActionContext ac) {
		return db.noTrx(() -> {
			Project project = ac.getProject();
			if (project != null) {
				return Collections.singleton(getIndexName(project.getUuid()));
			} else {
				return getIndices();
			}
		});
	}

	/**
	 * Get the index name for the given project
	 * 
	 * @param project
	 *            Uuid
	 * @return index name
	 */
	public String getIndexName(String projectUuid) {
		StringBuilder indexName = new StringBuilder("tag-family");
		indexName.append("-").append(projectUuid);
		return indexName.toString();
	}

	@Override
	protected String getType() {
		return "tagFamily";
	}

	@Override
	public String getKey() {
		return TagFamily.TYPE;
	}

	@Override
	protected RootVertex<TagFamily> getRootVertex() {
		return boot.meshRoot().getTagFamilyRoot();
	}

	@Override
	protected Map<String, Object> transformToDocumentMap(TagFamily tagFamily) {
		Map<String, Object> map = new HashMap<>();
		map.put(NAME_KEY, tagFamily.getName());
		addBasicReferences(map, tagFamily);
		addTags(map, tagFamily.getTagRoot().findAll());
		addProject(map, tagFamily.getProject());
		return map;
	}

	@Override
	protected JsonObject getMapping() {
		JsonObject props = new JsonObject();
		props.put(NAME_KEY, fieldType(STRING, NOT_ANALYZED));
		return props;
	}

}
