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

import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.runtime.api.Framework;

/**
 * Automation operation that resolves document types for a folder tree.
 * <p>
 * Receives a JSON array as a string parameter describing the tree structure
 * (folders and files) and returns a JSON blob with "docType" populated for each item.
 * <p>
 * If no callback chain is configured, defaults are used:
 * folders → "Folder", files → null (FileManager.Import will be used).
 * <p>
 * If a callback chain is configured, it is called for each item to determine
 * the document type.
 * <p>
 * This FolderDrop.ResolveTypes operation is mainly an internal operation. Developers
 * can still override it if they need to tune the behavior without changing the whole
 * plugin. Just create an operation of the same ID and make sure to deploy it after
 * this one (the best place is an operation in a Nuxeo Studio project)
 *
 * @since 2025.1
 */
@Operation(id = FolderDropResolveTypesOp.ID, category = "Document", label = "FolderDrop: Resolve Types", description = "Resolves document types for a folder tree. Takes the tree JSON as a string parameter, returns the same tree with docType populated.")
public class FolderDropResolveTypesOp {

    public static final String ID = "FolderDrop.ResolveTypes";

    @Context
    protected CoreSession session;

    @Param(name = "parentPath", required = true, description = "Path of the parent container document")
    protected String parentPath;

    @Param(name = "treeJson", required = true, description = "JSON array describing the tree structure")
    protected String treeJson;

    @OperationMethod
    public Blob run() {
        // Enforce that the caller is allowed to add children to the target parent.
        // The configured callback chain receives this parent as input, so without
        // this check any authenticated user could trigger chain side-effects on
        // any document they can read.
        var parentRef = new PathRef(parentPath);
        if (!session.hasPermission(parentRef, SecurityConstants.ADD_CHILDREN)) {
            throw new DocumentSecurityException(
                    "Privilege '" + SecurityConstants.ADD_CHILDREN + "' is not granted to '"
                            + session.getPrincipal().getName() + "' on document " + parentPath);
        }
        var service = Framework.getService(FolderDropService.class);
        var result = service.resolveTypes(session, treeJson, parentPath);
        return Blobs.createJSONBlob(result);
    }
}
