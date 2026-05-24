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

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * Descriptor for the FolderDrop service configuration.
 * <p>
 * Example XML contribution:
 * <pre>
 * &lt;extension target="nuxeo.labs.folderdrop.FolderDropService" point="configuration"&gt;
 *   &lt;configuration&gt;
 *     &lt;callbackChain&gt;myChainId&lt;/callbackChain&gt;
 *     &lt;mimeTypeDenyPatterns&gt;video/.*,application/x-executable&lt;/mimeTypeDenyPatterns&gt;
 *     &lt;filterHiddenFiles&gt;true&lt;/filterHiddenFiles&gt;
 *   &lt;/configuration&gt;
 * &lt;/extension&gt;
 * </pre>
 *
 * @since 2025.1
 */
@XObject("configuration")
public class FolderDropDescriptor {

    @XNode("callbackChain")
    protected String callbackChain;

    @XNode("mimeTypeDenyPatterns")
    protected String mimeTypeDenyPatterns;

    @XNode("filterHiddenFiles")
    protected String filterHiddenFiles;

    public String getCallbackChain() {
        return callbackChain;
    }

    public String getMimeTypeDenyPatterns() {
        return mimeTypeDenyPatterns;
    }

    /**
     * Returns true if hidden files should be filtered (default: true).
     */
    public boolean isFilterHiddenFiles() {
        if (StringUtils.isBlank(filterHiddenFiles)) {
            return true;
        }
        return !"false".equalsIgnoreCase(filterHiddenFiles.trim());
    }
}
