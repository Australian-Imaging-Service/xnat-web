/*
 * web: org.nrg.xnat.helpers.prearchive.handlers.PrearchiveRebuildHandler
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.helpers.prearchive.handlers;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.nrg.framework.services.NrgEventServiceI;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.bean.XnatImagesessiondataBean;
import org.nrg.xdat.bean.XnatPetmrsessiondataBean;
import org.nrg.xdat.bean.reader.XDATXMLReader;
import org.nrg.xdat.security.user.XnatUserProvider;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.helpers.prearchive.PrearcDatabase;
import org.nrg.xnat.helpers.prearchive.PrearcSession;
import org.nrg.xnat.helpers.prearchive.PrearcUtils;
import org.nrg.xnat.helpers.prearchive.SessionData;
import org.nrg.xnat.services.messaging.prearchive.PrearchiveOperationRequest;

import java.io.File;

import static org.nrg.xnat.archive.Operation.*;

@Handles(Rebuild)
@Slf4j
public class PrearchiveRebuildHandler extends AbstractPrearchiveOperationHandler {
    public PrearchiveRebuildHandler(final PrearchiveOperationRequest request, final NrgEventServiceI eventService, final XnatUserProvider userProvider) {
        super(request, eventService, userProvider);
    }

    @Override
    public void execute() {
        try {
            final boolean receiving = getSessionData().getStatus() != null && getSessionData().getStatus().equals(PrearcUtils.PrearcStatus.RECEIVING);
            log.info("Received request to process prearchive session at: {}", getSessionData().getExternalUrl());
            if (!getSessionDir().getParentFile().exists()) {
                try {
                    if (log.isInfoEnabled()) {
                        log.info("The parent of the indicated session " + getSessionData().getName() + " could not be found at the indicated location " + getSessionDir().getParentFile().getAbsolutePath());
                    }
                    PrearcDatabase.unsafeSetStatus(getSessionData().getFolderName(), getSessionData().getTimestamp(), getSessionData().getProject(), PrearcUtils.PrearcStatus._DELETING);
                    PrearcDatabase.deleteCacheRow(getSessionData().getFolderName(), getSessionData().getTimestamp(), getSessionData().getProject());
                } catch (Exception e) {
                    log.error("An error occurred attempting to clear the prearchive entry for the session " + getSessionData().getName() + ", which doesn't exist at the indicated location " + getSessionDir().getParentFile().getAbsolutePath());
                }
            } else if (PrearcDatabase.setStatus(getSessionData().getFolderName(), getSessionData().getTimestamp(), getSessionData().getProject(), PrearcUtils.PrearcStatus.BUILDING)) {
                PrearcDatabase.buildSession(getSessionDir(), getSessionData().getFolderName(), getSessionData().getTimestamp(), getSessionData().getProject(), getSessionData().getVisit(), getSessionData().getProtocol(), getSessionData().getTimeZone(), getSessionData().getSource());
                populateAdditionalFields(getSessionDir());

                // We need to check whether the session was updated to RECEIVING_INTERRUPT while the rebuild operation
                // was happening. If that happened, that means more data started to arrive during the rebuild. If not,
                // we'll proceed down the path where we check for session splits and autoarchive. If so, we'll just
                // reset the status to RECEIVING and update the session timestamp.
                final SessionData current = PrearcDatabase.getSession(getSessionData().getSessionDataTriple());
                if (current.getStatus() != PrearcUtils.PrearcStatus.RECEIVING_INTERRUPT) {
                    final boolean separatePetMr = PrearcUtils.isUnassigned(getSessionData()) ? PrearcUtils.shouldSeparatePetMr() : PrearcUtils.shouldSeparatePetMr(getSessionData().getProject());
                    if (separatePetMr) {
                        log.debug("Found create separate PET and MR sessions setting for project {}, now working to separate that.", getSessionData().getProject());
                        final File sessionXml = new File(getSessionDir() + ".xml");
                        if (sessionXml.exists()) {
                            log.debug("Found the session XML in the file {}, processing.", sessionXml.getAbsolutePath());
                            final XnatImagesessiondataBean bean = (XnatImagesessiondataBean) new XDATXMLReader().parse(sessionXml);
                            if (bean instanceof XnatPetmrsessiondataBean) {
                                log.debug("Found a PET/MR session XML in the file {} with the separate PET/MR flag set to true for the site or project, creating a new request to separate the session.", sessionXml.getAbsolutePath());
                                PrearcUtils.resetStatus(getUser(), getSessionData().getProject(), getSessionData().getTimestamp(), getSessionData().getFolderName(), true);
                                final PrearchiveOperationRequest request = new PrearchiveOperationRequest(getUser(), Separate, getSessionData(), getSessionDir());
                                XDAT.sendJmsRequest(request);
                            } else {
                                log.debug("Found a session XML for a {} session in the file {}. Not PET/MR so not separating.", bean.getFullSchemaElementName(), sessionXml.getAbsolutePath());
                            }
                        } else {
                            log.warn("Tried to rebuild a session from the path {}, but that session XML doesn't exist.", sessionXml.getAbsolutePath());
                        }
                    } else {
                        PrearcUtils.resetStatus(getUser(), getSessionData().getProject(), getSessionData().getTimestamp(), getSessionData().getFolderName(), true);

                        // we don't want to autoarchive a session that's just being rebuilt
                        // but we still want to autoarchive sessions that just came from RECEIVING STATE
                        final PrearcSession session = new PrearcSession(getSessionData().getProject(), getSessionData().getTimestamp(), getSessionData().getFolderName(), null, getUser());
                        if (receiving || !session.isAutoArchive()) {
                            final PrearchiveOperationRequest request = new PrearchiveOperationRequest(getUser(), Archive, getSessionData(), getSessionDir());
                            XDAT.sendJmsRequest(request);
                        }
                    }
                } else {
                    log.info("Found session {} in RECEIVING_INTERRUPT state, meaning that data began arriving while session was in an interruptible non-receiving state. No session split or autoarchive checks will be performed and session will be restored to RECEIVING state.", getSessionData().getSessionDataTriple());
                    PrearcDatabase.setStatus(getSessionData().getFolderName(), getSessionData().getTimestamp(), getSessionData().getProject(), PrearcUtils.PrearcStatus.RECEIVING);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected UserI getUser() {
        final XnatUserProvider provider = XDAT.getContextService().getBean("receivedFileUserProvider", XnatUserProvider.class);
        return ObjectUtils.defaultIfNull(provider != null ? provider.get() : null, super.getUser());
    }
}
