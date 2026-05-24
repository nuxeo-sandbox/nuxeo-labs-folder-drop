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
 */
package nuxeo.labs.folderdrop;

import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * Permanently deletes (NOT trashes) a list of top-level documents that were just created
 * by a folder drop, used to roll back a partial import when the user clicks "Stop and
 * permanently delete imported items".
 * <p>
 * The caller passes only the UUIDs of the <strong>top-level</strong> items (direct children
 * of the drop target). Nuxeo cascades deletion to all descendants, so subfolders and files
 * inside them are removed automatically.
 * <p>
 * Safety guarantees:
 * <ul>
 *   <li>Each UUID must reference a document whose direct parent is {@code parentId}.
 *       This prevents a tampered client from asking the server to delete arbitrary
 *       documents the user can otherwise remove.</li>
 *   <li>Deletion uses {@link CoreSession#removeDocuments(DocumentRef[])} — documents
 *       are <strong>permanently removed</strong>, not moved to the trash.</li>
 *   <li>Standard Remove permission is enforced by the core session.</li>
 * </ul>
 *
 * @since 2025.1
 */
@Operation(id = FolderDropRollbackImportOp.ID, category = "Document", label = "FolderDrop: Rollback Import", description = "Permanently deletes (NOT trashes) the listed top-level documents created by a folder drop. Each document must be a direct child of parentId. Irreversible.")
public class FolderDropRollbackImportOp {

    private static final Logger log = LogManager.getLogger(FolderDropRollbackImportOp.class);

    public static final String ID = "FolderDrop.RollbackImport";

    @Context
    protected CoreSession session;

    @Param(name = "parentId", required = true, description = "UUID of the drop-target parent document. All ids must be direct children of this parent.")
    protected String parentId;

    @Param(name = "ids", required = true, description = "Comma-separated list of top-level document UUIDs to permanently delete.")
    protected String ids;

    @OperationMethod
    public void run() {
        if (StringUtils.isBlank(ids)) {
            return;
        }
        var parentRef = new IdRef(parentId);
        if (!session.exists(parentRef)) {
            throw new NuxeoException("Parent document does not exist: " + parentId);
        }

        var parts = ids.split(",");
        var refs = new ArrayList<DocumentRef>(parts.length);
        var collected = new ArrayList<String>(parts.length);
        for (var raw : parts) {
            var trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var ref = new IdRef(trimmed);
            if (!session.exists(ref)) {
                // Already gone — skip silently.
                continue;
            }
            var doc = session.getDocument(ref);
            // Safety: every document must be a direct child of parentId.
            var actualParent = doc.getParentRef();
            if (actualParent == null || !actualParent.equals(parentRef)) {
                throw new DocumentSecurityException(
                        "Document " + trimmed + " is not a direct child of " + parentId
                                + "; refusing to delete it via FolderDrop.RollbackImport.");
            }
            refs.add(ref);
            collected.add(trimmed);
        }

        if (refs.isEmpty()) {
            return;
        }

        log.info("FolderDrop.RollbackImport: permanently deleting {} document(s) under parent {}: {}",
                refs.size(), parentId, collected);

        // Hard delete (NOT trash). Cascades to children automatically.
        session.removeDocuments(refs.toArray(new DocumentRef[0]));
    }
}
