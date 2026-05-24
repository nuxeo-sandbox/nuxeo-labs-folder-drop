/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Thibaud Arguillere
 *     (Code initially generated with the help of OpenCode / Claude Opus)
 */
package nuxeo.labs.folderdrop;

import java.util.List;

import org.nuxeo.ecm.core.api.CoreSession;

/**
 * Service interface for the FolderDrop plugin.
 * <p>
 * Provides the ability to resolve document types for a tree of folders and files
 * being imported via drag-and-drop. When a callback automation chain is configured,
 * it is called for each item in the tree to determine the document type to create.
 * <p>
 * Without a callback chain, folders default to "Folder" and files return null
 * (meaning FileManager.Import should be used).
 * <p>
 * Supports file filtering via MIME type deny patterns (regex) and hidden file filtering.
 *
 * @since 2025.1
 */
public interface FolderDropService {

    /**
     * Context variable name that the callback chain must set with the result JSON.
     * Expected format: {@code {"docType": "SomeType"}}
     */
    String CALLBACK_RESULT_CTX_VAR = "FolderDrop_Result";

    /** Parameter passed to the callback chain: the name of the item. */
    String PARAM_NAME = "name";

    /** Parameter passed to the callback chain: true if the item is a folder. */
    String PARAM_IS_FOLDER = "is_folder";

    /** Parameter passed to the callback chain: the MIME type (for files, empty for folders). */
    String PARAM_MIME_TYPE = "mime_type";

    /** Parameter passed to the callback chain: the file size in bytes (for files, 0 for folders). */
    String PARAM_SIZE = "size";

    /** Parameter passed to the callback chain: the relative path within the dropped tree. */
    String PARAM_RELATIVE_PATH = "relative_path";

    /**
     * Event name fired after a folder drop import completes (success, partial, or failure).
     * <p>
     * The event is a {@code DocumentEventContext} on the parent document, with the following
     * context properties:
     * <ul>
     *   <li>{@code status} — "success", "partial", or "failure"</li>
     *   <li>{@code parentId} — UUID of the parent document</li>
     *   <li>{@code droppedFolderCount} — number of folders in the dropped tree</li>
     *   <li>{@code droppedFileCount} — number of files in the dropped tree</li>
     *   <li>{@code createdCount} — number of documents actually created</li>
     *   <li>{@code failedItem} — relative path of the item that failed (null on success)</li>
     *   <li>{@code failedMessage} — server error message (null on success)</li>
     * </ul>
     *
     * @since 2025.1
     */
    String EVENT_IMPORT_DONE = "folderDropImportDone";

    /**
     * Returns true if a callback chain is configured.
     */
    boolean hasCallbackChain();

    /**
     * Returns the configured callback chain ID, or null if none.
     */
    String getCallbackChain();

    /**
     * Returns true if hidden files (names starting with '.') should be filtered.
     * Default is true.
     */
    boolean isFilterHiddenFiles();

    /**
     * Returns the list of MIME type deny patterns (regex strings).
     * Returns an empty list if none are configured.
     */
    List<String> getMimeTypeDenyPatterns();

    /**
     * Checks if a given MIME type matches any of the configured deny patterns.
     *
     * @param mimeType the MIME type to check
     * @return true if the MIME type is denied
     */
    boolean isMimeTypeDenied(String mimeType);

    /**
     * Resolves document types for a tree of items.
     * <p>
     * Input JSON is an array of items:
     * <pre>
     * [
     *   {"name": "folder1", "relativePath": "folder1", "isFolder": true},
     *   {"name": "file1.pdf", "relativePath": "folder1/file1.pdf", "isFolder": false,
     *    "mimeType": "application/pdf", "size": 12345, "batchFileIndex": 0}
     * ]
     * </pre>
     * <p>
     * Returns the same array with a "docType" field added to each item.
     * For default behavior (no callback): folders get "Folder", files get null.
     * With a callback chain: the chain determines the docType for each item.
     * <p>
     * Before resolving types, the method validates items against the configured
     * file filters (hidden files, MIME type deny patterns). If any denied items
     * are found, a {@link org.nuxeo.ecm.core.api.NuxeoException} is thrown with
     * details about the rejected files, as they should have been filtered client-side.
     *
     * @param session the core session
     * @param treeJson the JSON array of items to resolve
     * @param parentPath the path of the parent container document
     * @return JSON array with docType populated
     */
    String resolveTypes(CoreSession session, String treeJson, String parentPath);
}
