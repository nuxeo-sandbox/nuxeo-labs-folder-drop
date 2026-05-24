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
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.runtime.api.Framework;

/**
 * Fires a {@code folderDropImportDone} event after a folder drop import completes.
 * <p>
 * This operation is called by the client after all documents have been created
 * (or after a failure), so the event fires outside of the import transaction.
 * <p>
 * Listeners for this event should be registered with {@code async="true"} to avoid
 * blocking the operation and the client.
 *
 * @since 2025.1
 */
@Operation(id = FolderDropNotifyDoneOp.ID, category = "Document", label = "FolderDrop: Notify Done", description = "Fires the folderDropImportDone event after a folder drop import completes.")
public class FolderDropNotifyDoneOp {

    public static final String ID = "FolderDrop.NotifyDone";

    @Context
    protected CoreSession session;

    @Param(name = "parentId", required = true, description = "UUID of the target parent document")
    protected String parentId;

    @Param(name = "status", required = true, description = "Import status: success, partial, or failure")
    protected String status;

    @Param(name = "droppedFolderCount", required = true, description = "Number of folders in the dropped tree")
    protected int droppedFolderCount;

    @Param(name = "droppedFileCount", required = true, description = "Number of files in the dropped tree")
    protected int droppedFileCount;

    @Param(name = "createdCount", required = true, description = "Number of documents actually created")
    protected int createdCount;

    @Param(name = "failedItem", required = false, description = "Relative path of the item that failed")
    protected String failedItem;

    @Param(name = "failedMessage", required = false, description = "Server error message for the failure")
    protected String failedMessage;

    @OperationMethod
    public void run() {
        var parentDoc = session.getDocument(new IdRef(parentId));
        var eventCtx = new DocumentEventContext(session, session.getPrincipal(), parentDoc);

        eventCtx.setProperty("status", status);
        eventCtx.setProperty("parentId", parentId);
        eventCtx.setProperty("droppedFolderCount", droppedFolderCount);
        eventCtx.setProperty("droppedFileCount", droppedFileCount);
        eventCtx.setProperty("createdCount", createdCount);
        eventCtx.setProperty("failedItem", failedItem);
        eventCtx.setProperty("failedMessage", failedMessage);

        Event event = eventCtx.newEvent(FolderDropService.EVENT_IMPORT_DONE);
        Framework.getService(EventService.class).fireEvent(event);
    }
}
