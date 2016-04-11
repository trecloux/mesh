package com.gentics.mesh.core.data.node.field;

import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.node.field.nesting.ListableGraphField;
import com.gentics.mesh.core.link.WebRootLinkReplacer;
import com.gentics.mesh.core.rest.node.field.HtmlField;
import com.gentics.mesh.core.rest.node.field.impl.HtmlFieldImpl;

import rx.Observable;

/**
 * The HtmlField Domain Model interface.
 * 
 * A html graph field is a basic node field which can be used to store html string values.
 */
public interface HtmlGraphField extends ListableGraphField, BasicGraphField<HtmlField> {

	FieldTransformator HTML_TRANSFORMATOR = (container, ac, fieldKey, fieldSchema, languageTags, level, parentNode) -> {
		HtmlGraphField graphHtmlField = container.getHtml(fieldKey);
		if (graphHtmlField == null) {
			return Observable.just(new HtmlFieldImpl());
		} else {
			return graphHtmlField.transformToRest(ac).map(model -> {
				// If needed resolve links within the html
				if (ac.getResolveLinksType() != WebRootLinkReplacer.Type.OFF) {
					Project project = ac.getProject();
					if (project == null) {
						project = parentNode.getProject();
					}
					model.setHTML(
							WebRootLinkReplacer.getInstance().replace(model.getHTML(), ac.getResolveLinksType(), project.getName(), languageTags));
				}
				return model;
			});
		}
	};

	FieldUpdater  HTML_UPDATER = (container, ac, fieldKey, restField, fieldSchema, schema) -> {
		HtmlGraphField htmlGraphField = container.getHtml(fieldKey);
		GraphField.failOnMissingMandatoryField(ac, htmlGraphField, restField, fieldSchema, fieldKey, schema);
		HtmlField htmlField = (HtmlFieldImpl) restField;
		if (restField == null) {
			return;
		}

		// Create new graph field if no existing one could be found
		if (htmlGraphField == null) {
			container.createHTML(fieldKey).setHtml(htmlField.getHTML());
		} else {
			htmlGraphField.setHtml(htmlField.getHTML());
		}
	};

	FieldGetter  HTML_GETTER = (container, fieldSchema) -> {
		return container.getHtml(fieldSchema.getName());
	};

	/**
	 * Set the html field value for the field.
	 * 
	 * @param html
	 */
	void setHtml(String html);

	/**
	 * Return the html field value for the field.
	 * 
	 * @return
	 */
	String getHTML();

}
