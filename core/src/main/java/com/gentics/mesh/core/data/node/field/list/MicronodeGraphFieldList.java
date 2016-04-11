package com.gentics.mesh.core.data.node.field.list;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.node.Micronode;
import com.gentics.mesh.core.data.node.field.FieldGetter;
import com.gentics.mesh.core.data.node.field.FieldTransformator;
import com.gentics.mesh.core.data.node.field.FieldUpdater;
import com.gentics.mesh.core.data.node.field.GraphField;
import com.gentics.mesh.core.data.node.field.nesting.MicronodeGraphField;
import com.gentics.mesh.core.rest.node.field.MicronodeField;
import com.gentics.mesh.core.rest.node.field.list.MicronodeFieldList;
import com.gentics.mesh.core.rest.node.field.list.impl.MicronodeFieldListImpl;

import rx.Observable;

public interface MicronodeGraphFieldList extends ListGraphField<MicronodeGraphField, MicronodeFieldList, Micronode> {

	String TYPE = "micronode";

	FieldTransformator MICRONODE_LIST_TRANSFORMATOR = (container, ac, fieldKey, fieldSchema, languageTags, level, parentNode) -> {
		MicronodeGraphFieldList graphMicroschemaField = container.getMicronodeList(fieldKey);
		if (graphMicroschemaField == null) {
			return Observable.just(new MicronodeFieldListImpl());
		} else {
			return graphMicroschemaField.transformToRest(ac, fieldKey, languageTags, level);
		}
	};

	FieldUpdater MICRONODE_LIST_UPDATER = (container, ac, fieldKey, restField, fieldSchema, schema) -> {
		MicronodeGraphFieldList micronodeGraphFieldList = container.getMicronodeList(fieldKey);
		GraphField.failOnMissingMandatoryField(ac, micronodeGraphFieldList, restField, fieldSchema, fieldKey, schema);
		MicronodeFieldList micronodeList = (MicronodeFieldList) restField;

		if (micronodeList.getItems().isEmpty()) {
			if (micronodeGraphFieldList != null) {
				micronodeGraphFieldList.removeField(container);
			}
		} else {
			micronodeGraphFieldList = container.createMicronodeFieldList(fieldKey);
			//TODO instead this method should also return an observable 
			micronodeGraphFieldList.update(ac, micronodeList).toBlocking().last();
		}

	};

	FieldGetter  MICRONODE_LIST_GETTER = (container, fieldSchema) -> {
		return container.getMicronodeList(fieldSchema.getName());
	};

	/**
	 * Create a new micronode using the rest model as a source.
	 * 
	 * @param field
	 * @return
	 */
	Micronode createMicronode(MicronodeField field);

	/**
	 * Update the micronode list using the rest model list as a source.
	 * 
	 * @param ac
	 * @param list
	 * @return
	 */
	Observable<Boolean> update(InternalActionContext ac, MicronodeFieldList list);
}
