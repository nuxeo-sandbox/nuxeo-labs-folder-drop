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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;

/**
 * Test listener that captures folderDropImportDone events for assertion in tests.
 *
 * @since 2025.1
 */
public class TestFolderDropListener implements EventListener {

    protected static final List<Map<String, Object>> RECEIVED_EVENTS = new ArrayList<>();

    @Override
    public void handleEvent(Event event) {
        if (!FolderDropService.EVENT_IMPORT_DONE.equals(event.getName())) {
            return;
        }
        EventContext ctx = event.getContext();
        RECEIVED_EVENTS.add(Map.of(
                "status", ctx.getProperty("status"),
                "parentId", ctx.getProperty("parentId"),
                "droppedFolderCount", ctx.getProperty("droppedFolderCount"),
                "droppedFileCount", ctx.getProperty("droppedFileCount"),
                "createdCount", ctx.getProperty("createdCount")
        ));
    }

    public static void reset() {
        RECEIVED_EVENTS.clear();
    }

    public static List<Map<String, Object>> getReceivedEvents() {
        return RECEIVED_EVENTS;
    }
}
