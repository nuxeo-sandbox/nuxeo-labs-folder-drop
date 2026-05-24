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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.platform.test.PlatformFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

@RunWith(FeaturesRunner.class)
@Features({ PlatformFeature.class })
@Deploy("org.nuxeo.ecm.automation.core")
@Deploy("org.nuxeo.ecm.automation.scripting")
@Deploy("org.nuxeo.ecm.platform.types")
@Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core")
public class TestFolderDropService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TREE_JSON = "["
            + "{\"name\":\"folder1\",\"relativePath\":\"folder1\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"file1.pdf\",\"relativePath\":\"folder1/file1.pdf\",\"isFolder\":false,\"mimeType\":\"application/pdf\",\"size\":12345,\"batchFileIndex\":0},"
            + "{\"name\":\"image.png\",\"relativePath\":\"folder1/image.png\",\"isFolder\":false,\"mimeType\":\"image/png\",\"size\":67890,\"batchFileIndex\":1},"
            + "{\"name\":\"subfolder\",\"relativePath\":\"folder1/subfolder\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"doc.txt\",\"relativePath\":\"folder1/subfolder/doc.txt\",\"isFolder\":false,\"mimeType\":\"text/plain\",\"size\":100,\"batchFileIndex\":2},"
            + "{\"name\":\"folder2\",\"relativePath\":\"folder2\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0}"
            + "]";

    /** Tree containing a video file (denied by test-deny-patterns.xml). */
    private static final String TREE_WITH_VIDEO = "["
            + "{\"name\":\"folder1\",\"relativePath\":\"folder1\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"movie.mp4\",\"relativePath\":\"folder1/movie.mp4\",\"isFolder\":false,\"mimeType\":\"video/mp4\",\"size\":99999,\"batchFileIndex\":0}"
            + "]";

    /** Tree containing a hidden file. */
    private static final String TREE_WITH_HIDDEN = "["
            + "{\"name\":\"folder1\",\"relativePath\":\"folder1\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\".DS_Store\",\"relativePath\":\"folder1/.DS_Store\",\"isFolder\":false,\"mimeType\":\"application/octet-stream\",\"size\":100,\"batchFileIndex\":0},"
            + "{\"name\":\"file1.pdf\",\"relativePath\":\"folder1/file1.pdf\",\"isFolder\":false,\"mimeType\":\"application/pdf\",\"size\":12345,\"batchFileIndex\":1}"
            + "]";

    /** Tree with only clean files (no denied content). */
    private static final String TREE_CLEAN = "["
            + "{\"name\":\"folder1\",\"relativePath\":\"folder1\",\"isFolder\":true,\"mimeType\":\"\",\"size\":0},"
            + "{\"name\":\"file1.pdf\",\"relativePath\":\"folder1/file1.pdf\",\"isFolder\":false,\"mimeType\":\"application/pdf\",\"size\":12345,\"batchFileIndex\":0}"
            + "]";

    @Inject
    protected CoreSession session;

    @Inject
    protected FolderDropService service;

    @Inject
    protected AutomationService automationService;

    protected DocumentModel testFolder;

    @Before
    public void init() {
        testFolder = session.createDocumentModel("/", "test", "Folder");
        testFolder = session.createDocument(testFolder);
        session.save();
    }

    @Test
    public void testServiceIsDeployed() {
        assertNotNull(service);
    }

    @Test
    public void testNoCallbackChainByDefault() {
        assertFalse(service.hasCallbackChain());
    }

    @Test
    public void testResolveTypesNoCallback() throws IOException {
        var result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        var items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(6, items.size());

        // folder1 -> "Folder"
        assertEquals("Folder", items.get(0).get("docType").asText());

        // file1.pdf -> null (FileManager.Import)
        assertTrue(items.get(1).get("docType").isNull());

        // image.png -> null
        assertTrue(items.get(2).get("docType").isNull());

        // subfolder -> "Folder"
        assertEquals("Folder", items.get(3).get("docType").asText());

        // doc.txt -> null
        assertTrue(items.get(4).get("docType").isNull());

        // folder2 -> "Folder"
        assertEquals("Folder", items.get(5).get("docType").asText());
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-chain.xml")
    public void testResolveTypesWithCallback() throws IOException {
        assertTrue(service.hasCallbackChain());
        assertEquals("javascript.testFolderDropCallback", service.getCallbackChain());

        var result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        var items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(6, items.size());

        // folder1 -> "Workspace" (the test chain returns Workspace for folders)
        assertEquals("Workspace", items.get(0).get("docType").asText());

        // file1.pdf -> "File" (the test chain returns File for application/*)
        assertEquals("File", items.get(1).get("docType").asText());

        // image.png -> "Picture" (the test chain returns Picture for image/*)
        assertEquals("Picture", items.get(2).get("docType").asText());

        // subfolder -> "Workspace"
        assertEquals("Workspace", items.get(3).get("docType").asText());

        // doc.txt -> "Note" (the test chain returns Note for text/*)
        assertEquals("Note", items.get(4).get("docType").asText());

        // folder2 -> "Workspace"
        assertEquals("Workspace", items.get(5).get("docType").asText());
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-chain.xml")
    public void testResolveTypesOperation() throws OperationException, IOException {
        try (var ctx = new OperationContext(session)) {
            var params = new HashMap<String, Object>();
            params.put("parentPath", testFolder.getPathAsString());
            params.put("treeJson", TREE_JSON);

            var result = (Blob) automationService.run(ctx, FolderDropResolveTypesOp.ID, params);
            assertNotNull(result);

            var items = (ArrayNode) MAPPER.readTree(result.getString());
            assertEquals(6, items.size());
            assertEquals("Workspace", items.get(0).get("docType").asText());
            assertEquals("Picture", items.get(2).get("docType").asText());
        }
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-partial.xml")
    public void testResolveTypesCallbackReturnsPartial() throws IOException {
        // This chain only sets docType for folders, leaves files with no result -> defaults
        var result = service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
        assertNotNull(result);

        var items = (ArrayNode) MAPPER.readTree(result);

        // folder1 -> "OrderedFolder" (set by chain)
        assertEquals("OrderedFolder", items.get(0).get("docType").asText());

        // file1.pdf -> default "Folder" fallback won't apply; isFolder=false -> null
        assertTrue(items.get(1).get("docType").isNull());

        // subfolder -> "OrderedFolder"
        assertEquals("OrderedFolder", items.get(3).get("docType").asText());
    }

    // ==================== File Filtering Tests ====================

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-deny-patterns.xml")
    public void testDenyPatternRejectsVideo() {
        // video/mp4 should be denied by the pattern video/.*
        try {
            service.resolveTypes(session, TREE_WITH_VIDEO, testFolder.getPathAsString());
            fail("Expected NuxeoException for denied MIME type");
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("movie.mp4"));
            assertTrue(e.getMessage().contains("video/mp4"));
            assertTrue(e.getMessage().contains("client-side tampering"));
        }
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-deny-patterns.xml")
    public void testDenyPatternAllowsCleanFiles() throws IOException {
        // application/pdf should not be denied by video/.* or application/x-executable
        var result = service.resolveTypes(session, TREE_CLEAN, testFolder.getPathAsString());
        assertNotNull(result);
        var items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(2, items.size());
    }

    @Test
    public void testHiddenFileRejectedByDefault() {
        // Default config has filterHiddenFiles=true
        try {
            service.resolveTypes(session, TREE_WITH_HIDDEN, testFolder.getPathAsString());
            fail("Expected NuxeoException for hidden file");
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains(".DS_Store"));
            assertTrue(e.getMessage().contains("hidden file/folder"));
            assertTrue(e.getMessage().contains("client-side tampering"));
        }
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-no-hidden-filter.xml")
    public void testHiddenFileAllowedWhenDisabled() throws IOException {
        assertFalse(service.isFilterHiddenFiles());
        // With filterHiddenFiles=false, hidden files should pass through
        var result = service.resolveTypes(session, TREE_WITH_HIDDEN, testFolder.getPathAsString());
        assertNotNull(result);
        var items = (ArrayNode) MAPPER.readTree(result);
        assertEquals(3, items.size());
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-deny-patterns.xml")
    public void testIsMimeTypeDenied() {
        assertTrue(service.isMimeTypeDenied("video/mp4"));
        assertTrue(service.isMimeTypeDenied("video/avi"));
        assertTrue(service.isMimeTypeDenied("application/x-executable"));
        assertFalse(service.isMimeTypeDenied("application/pdf"));
        assertFalse(service.isMimeTypeDenied("image/png"));
        assertFalse(service.isMimeTypeDenied("text/plain"));
    }

    @Test
    public void testFilterHiddenFilesDefaultIsTrue() {
        assertTrue(service.isFilterHiddenFiles());
    }

    @Test
    public void testNoDenyPatternsByDefault() {
        assertTrue(service.getMimeTypeDenyPatterns().isEmpty());
    }

    // ==================== NotifyDone / Event Tests ====================

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-listener.xml")
    public void testNotifyDoneFiresEvent() throws OperationException {
        TestFolderDropListener.reset();

        try (var ctx = new OperationContext(session)) {
            var params = new HashMap<String, Object>();
            params.put("parentId", testFolder.getId());
            params.put("status", "success");
            params.put("droppedFolderCount", 3);
            params.put("droppedFileCount", 10);
            params.put("createdCount", 13);

            automationService.run(ctx, FolderDropNotifyDoneOp.ID, params);
        }

        var events = TestFolderDropListener.getReceivedEvents();
        assertEquals(1, events.size());

        var event = events.get(0);
        assertEquals("success", event.get("status"));
        assertEquals(testFolder.getId(), event.get("parentId"));
        assertEquals(3, event.get("droppedFolderCount"));
        assertEquals(10, event.get("droppedFileCount"));
        assertEquals(13, event.get("createdCount"));
    }

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-listener.xml")
    public void testNotifyDonePartialFailure() throws OperationException {
        TestFolderDropListener.reset();

        try (var ctx = new OperationContext(session)) {
            var params = new HashMap<String, Object>();
            params.put("parentId", testFolder.getId());
            params.put("status", "partial");
            params.put("droppedFolderCount", 3);
            params.put("droppedFileCount", 10);
            params.put("createdCount", 5);
            params.put("failedItem", "folder1/subfolder/doc.txt");
            params.put("failedMessage", "Permission denied");

            automationService.run(ctx, FolderDropNotifyDoneOp.ID, params);
        }

        var events = TestFolderDropListener.getReceivedEvents();
        assertEquals(1, events.size());

        var event = events.get(0);
        assertEquals("partial", event.get("status"));
        assertEquals(5, event.get("createdCount"));
    }

    // ==================== Callback Chain Error Propagation Tests ====================

    @Test
    @Deploy("nuxeo.labs.folderdrop.nuxeo-labs-folder-drop-core:test-callback-error.xml")
    public void testCallbackChainErrorPropagates() {
        assertTrue(service.hasCallbackChain());
        try {
            service.resolveTypes(session, TREE_JSON, testFolder.getPathAsString());
            fail("Expected NuxeoException from failing callback chain");
        } catch (NuxeoException e) {
            assertTrue(e.getMessage().contains("Callback chain"));
            assertTrue(e.getMessage().contains("failed for item"));
            assertTrue(e.getMessage().contains("Custom validation error"));
        }
    }
}
