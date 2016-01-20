package com.gentics.mesh.rest.method;

import com.gentics.mesh.core.rest.common.GenericMessageResponse;
import com.gentics.mesh.core.rest.node.NodeDownloadResponse;
import com.gentics.mesh.query.QueryParameterProvider;
import com.gentics.mesh.query.impl.ImageManipulationParameter;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

/**
 * Interface for Node Field API specific rest API methods.
 */
public interface NodeFieldAPIClientMethods {

	/**
	 * Update the binary field for the node with the given nodeUuid in the given project using the provided data buffer.
	 * 
	 * @param projectName
	 *            Name of the project which contains the node
	 * @param nodeUuid
	 *            Uuid of the node
	 * @param languageTag
	 *            Language tag of the node
	 * @param fieldKey
	 *            Key of the field which holds the binary data
	 * @param fileData
	 *            Buffer that serves the binary data
	 * @param fileName
	 * @param contentType
	 * @return
	 */
	Future<GenericMessageResponse> updateNodeBinaryField(String projectName, String nodeUuid, String languageTag, String fieldKey, Buffer fileData,
			String fileName, String contentType);

	/**
	 * Download the binary field of the given node in the given project.
	 * 
	 * @param projectName
	 * @param nodeUuid
	 * @param languageTag
	 * @param fieldKey
	 * @param parameters
	 * @return Future with a download response that contains a reference to the byte buffer with the binary data
	 */
	Future<NodeDownloadResponse> downloadBinaryField(String projectName, String nodeUuid, String languageTag, String fieldKey,
			QueryParameterProvider... parameters);

	/**
	 * Transform the binary field of the given node in the given project
	 * 
	 * @param projectName project name
	 * @param nodeUuid uuid of hte node
	 * @param languageTag language tag
	 * @param fieldKey field key
	 * @param imageManipulationParameter parameters for the image transformation
	 * @return future with the generic message from the response
	 */
	Future<GenericMessageResponse> transformNodeBinaryField(String projectName, String nodeUuid, String languageTag,
			String fieldKey, ImageManipulationParameter imageManipulationParameter);
}
