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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implementation of the {@link FolderDropService}.
 * <p>
 * Registers an extension point "configuration" that accepts a {@link FolderDropDescriptor}
 * with an optional callbackChain, mimeTypeDenyPatterns, and filterHiddenFiles.
 *
 * @since 2025.1
 */
public class FolderDropServiceImpl extends DefaultComponent implements FolderDropService {

    private static final Logger log = LogManager.getLogger(FolderDropServiceImpl.class);

    public static final String EXT_POINT = "configuration";

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    protected static final int MAX_ITEMS = 10000;

    protected FolderDropDescriptor descriptor;

    /** Compiled deny patterns, built on registration and cached. */
    protected List<Pattern> compiledDenyPatterns;

    /** Raw deny pattern strings, parsed from the descriptor. */
    protected List<String> denyPatternStrings;

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (EXT_POINT.equals(extensionPoint)) {
            descriptor = (FolderDropDescriptor) contribution;
            // Parse and validate deny patterns at startup
            compiledDenyPatterns = new ArrayList<>();
            denyPatternStrings = new ArrayList<>();
            var rawPatterns = descriptor.getMimeTypeDenyPatterns();
            if (StringUtils.isNotBlank(rawPatterns)) {
                var parts = rawPatterns.split(",");
                for (var part : parts) {
                    var trimmed = part.trim();
                    if (StringUtils.isNotBlank(trimmed)) {
                        try {
                            compiledDenyPatterns.add(Pattern.compile(trimmed));
                            denyPatternStrings.add(trimmed);
                        } catch (PatternSyntaxException e) {
                            throw new NuxeoException(
                                    "Invalid MIME type deny pattern '%s': %s".formatted(trimmed, e.getMessage()), e);
                        }
                    }
                }
            }
            log.info("FolderDrop configuration registered: callbackChain='{}', filterHiddenFiles={}, denyPatterns={}",
                    descriptor.getCallbackChain(), descriptor.isFilterHiddenFiles(), denyPatternStrings);
        }
    }

    @Override
    public boolean hasCallbackChain() {
        return descriptor != null && StringUtils.isNotBlank(descriptor.getCallbackChain());
    }

    @Override
    public String getCallbackChain() {
        return descriptor != null ? descriptor.getCallbackChain() : null;
    }

    @Override
    public boolean isFilterHiddenFiles() {
        return descriptor == null || descriptor.isFilterHiddenFiles();
    }

    @Override
    public List<String> getMimeTypeDenyPatterns() {
        return denyPatternStrings != null ? Collections.unmodifiableList(denyPatternStrings)
                : Collections.emptyList();
    }

    @Override
    public boolean isMimeTypeDenied(String mimeType) {
        if (StringUtils.isBlank(mimeType) || compiledDenyPatterns == null || compiledDenyPatterns.isEmpty()) {
            return false;
        }
        for (var pattern : compiledDenyPatterns) {
            try {
                if (pattern.matcher(mimeType).matches()) {
                    return true;
                }
            } catch (Exception e) {
                throw new NuxeoException(
                        "Error evaluating MIME type deny pattern '%s' against MIME type '%s'".formatted(
                                pattern.pattern(), mimeType),
                        e);
            }
        }
        return false;
    }

    @Override
    public String resolveTypes(CoreSession session, String treeJson, String parentPath) {
        try {
            var parsed = OBJECT_MAPPER.readTree(treeJson);
            if (!(parsed instanceof ArrayNode items)) {
                throw new NuxeoException("Invalid tree JSON: expected a JSON array");
            }

            if (items.size() > MAX_ITEMS) {
                throw new NuxeoException(
                        "Too many items in tree (%d). Maximum allowed is %d".formatted(items.size(), MAX_ITEMS));
            }

            // Validate items against file filters before resolving types
            validateFilters(items);

            if (!hasCallbackChain()) {
                return resolveDefaults(items);
            }

            return resolveWithCallback(session, items, parentPath);
        } catch (IOException e) {
            throw new NuxeoException("Failed to parse tree JSON", e);
        }
    }

    /**
     * Validates all items against configured file filters.
     * <p>
     * Throws a NuxeoException if any denied items are found, listing them with reasons.
     * Items that reach the server despite being denied should have been filtered client-side,
     * which may indicate client-side tampering.
     */
    protected void validateFilters(ArrayNode items) {
        var filterHidden = isFilterHiddenFiles();
        var hasDenyPatterns = compiledDenyPatterns != null && !compiledDenyPatterns.isEmpty();

        if (!filterHidden && !hasDenyPatterns) {
            return;
        }

        var rejectedItems = new ArrayList<String>();

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (!(item instanceof ObjectNode obj)) {
                continue;
            }
            var name = obj.path("name").asText("");
            var isFolder = obj.path("isFolder").asBoolean(false);
            var mimeType = obj.path("mimeType").asText("");
            var relativePath = obj.path("relativePath").asText(name);

            // Check hidden files/folders
            if (filterHidden && !name.isEmpty() && name.charAt(0) == '.') {
                rejectedItems.add(relativePath + " (hidden file/folder)");
                continue;
            }

            // Check MIME type deny patterns (only for files, not folders)
            if (!isFolder && hasDenyPatterns && isMimeTypeDenied(mimeType)) {
                rejectedItems.add(relativePath + " (denied MIME type: " + mimeType + ")");
            }
        }

        if (!rejectedItems.isEmpty()) {
            throw new NuxeoException(
                    "The following files should have been filtered client-side but were received by the server, "
                            + "which may indicate client-side tampering. Rejected: " + rejectedItems);
        }
    }

    /**
     * Resolves types using defaults: folders get "Folder", files get null (FileManager.Import).
     */
    protected String resolveDefaults(ArrayNode items) {
        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (!(item instanceof ObjectNode obj)) {
                throw new NuxeoException("Invalid tree item at index %d: expected a JSON object".formatted(i));
            }
            var isFolder = obj.path("isFolder").asBoolean(false);
            if (isFolder) {
                obj.put("docType", "Folder");
            } else {
                obj.putNull("docType");
            }
        }
        return items.toString();
    }

    /**
     * Resolves types by calling the configured automation chain for each item.
     * <p>
     * The chain receives the parent document as input and item properties as parameters.
     * It must set the context variable {@link #CALLBACK_RESULT_CTX_VAR} to a JSON string
     * like {@code {"docType": "MyType"}}.
     */
    protected String resolveWithCallback(CoreSession session, ArrayNode items, String parentPath) {
        var chainId = descriptor.getCallbackChain();
        var automationService = Framework.getService(AutomationService.class);
        var parentDoc = session.getDocument(new PathRef(parentPath));

        for (int i = 0; i < items.size(); i++) {
            var item = items.get(i);
            if (!(item instanceof ObjectNode obj)) {
                throw new NuxeoException("Invalid tree item at index %d: expected a JSON object".formatted(i));
            }
            var name = obj.path("name").asText("");
            var isFolder = obj.path("isFolder").asBoolean(false);
            var mimeType = obj.path("mimeType").asText("");
            var size = obj.path("size").asLong(0);
            var relativePath = obj.path("relativePath").asText("");

            try (var ctx = new OperationContext(session)) {
                ctx.setInput(parentDoc);
                Map<String, Object> params = new HashMap<>();
                params.put(PARAM_NAME, name);
                params.put(PARAM_IS_FOLDER, isFolder);
                params.put(PARAM_MIME_TYPE, mimeType);
                params.put(PARAM_SIZE, size);
                params.put(PARAM_RELATIVE_PATH, relativePath);

                automationService.run(ctx, chainId, params);

                var resultStr = (String) ctx.get(CALLBACK_RESULT_CTX_VAR);
                if (StringUtils.isNotBlank(resultStr)) {
                    var resultJson = OBJECT_MAPPER.readTree(resultStr);
                    var docTypeNode = resultJson.get("docType");
                    if (docTypeNode != null && !docTypeNode.isNull()) {
                        obj.put("docType", docTypeNode.asText());
                    } else {
                        setDefaultDocType(obj, isFolder);
                    }
                } else {
                    setDefaultDocType(obj, isFolder);
                }
            } catch (OperationException | IOException e) {
                var rootMessage = e.getMessage();
                var cause = e.getCause();
                while (cause != null) {
                    if (cause.getMessage() != null) {
                        rootMessage = cause.getMessage();
                    }
                    cause = cause.getCause();
                }
                throw new NuxeoException(
                        "Callback chain '%s' failed for item '%s': %s".formatted(chainId, relativePath,
                                rootMessage),
                        e);
            }
        }

        return items.toString();
    }

    protected void setDefaultDocType(ObjectNode obj, boolean isFolder) {
        if (isFolder) {
            obj.put("docType", "Folder");
        } else {
            obj.putNull("docType");
        }
    }
}
