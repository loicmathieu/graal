/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.persist;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;

/**
 *
 * @author sdedic
 */
public abstract class AbstractCatalogStorage implements ComponentStorage {
    protected final ComponentRegistry localRegistry;
    protected final Feedback feedback;
    protected final URL baseURL;

    public AbstractCatalogStorage(ComponentRegistry localRegistry, Feedback feedback, URL baseURL) {
        this.localRegistry = localRegistry;
        this.feedback = feedback;
        this.baseURL = baseURL;
    }

    public static byte[] toHashBytes(String comp, String hashS, Feedback fb) {
        String val = hashS.trim();
        if (val.length() < 4) {
            throw fb.failure("REMOTE_InvalidHash", null, comp, val);
        }
        char c = val.charAt(2);
        boolean divided = !((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')); // NOI18N
        boolean lenOK;
        int s;
        if (divided) {
            lenOK = (val.length() + 1) % 3 == 0;
            s = (val.length() + 1) / 3;
        } else {
            lenOK = (val.length()) % 2 == 0;
            s = (val.length() + 1) / 2;
        }
        if (!lenOK) {
            throw fb.failure("REMOTE_InvalidHash", null, comp, val);
        }
        byte[] digest = new byte[s];
        int dI = 0;
        for (int i = 0; i + 1 < val.length(); i += 2) {
            int b;
            try {
                b = Integer.parseInt(val.substring(i, i + 2), 16);
            } catch (NumberFormatException ex) {
                throw new MetadataException(null, fb.l10n("REMOTE_InvalidHash", comp, val));
            }
            if (b < 0) {
                throw new MetadataException(null, fb.l10n("REMOTE_InvalidHash", comp, val));
            }
            digest[dI++] = (byte) b;
            if (divided) {
                i++;
            }
        }
        return digest;
    }
    
    @Override
    public Map<String, String> loadGraalVersionInfo() {
        return localRegistry.getGraalCapabilities();
    }

    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        // files are not supported, yet
        return ci;
    }

    protected byte[] toHashBytes(String comp, String hashS) {
        return toHashBytes(comp, hashS, feedback);
    }

    @Override
    public void deleteComponent(String id) throws IOException {
        throw new UnsupportedOperationException("Read only"); // NOI18N
    }

    @Override
    public Map<String, Collection<String>> readReplacedFiles() throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }

    @Override
    public void saveComponent(ComponentInfo info) throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }

    @Override
    public Date licenseAccepted(ComponentInfo info, String licenseID) {
        return null;
    }

    @Override
    public void recordLicenseAccepted(ComponentInfo info, String licenseID, String licenseText) throws IOException {
    }
    

    @Override
    public void updateReplacedFiles(Map<String, Collection<String>> replacedFiles) throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }
}
