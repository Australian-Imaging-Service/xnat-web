/*
 * web: org.nrg.xnat.archive.FinishImageUpload
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.archive;

import org.nrg.action.ActionException;
import org.nrg.action.ClientException;
import org.nrg.action.ServerException;
import org.nrg.framework.constants.PrearchiveCode;
import org.nrg.framework.status.StatusProducer;
import org.nrg.framework.status.StatusProducerI;
import org.nrg.xft.XFTItem;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.schema.Wrappers.XMLWrapper.SAXReader;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.helpers.prearchive.PrearcDatabase;
import org.nrg.xnat.helpers.prearchive.PrearcUtils;
import org.nrg.xnat.helpers.prearchive.SessionData;
import org.nrg.xnat.helpers.uri.URIManager;
import org.nrg.xnat.helpers.xmlpath.XMLPathShortcuts;
import org.nrg.xnat.restlet.actions.PrearcImporterA.PrearcSession;
import org.nrg.xnat.restlet.util.RequestUtil;
import org.nrg.xnat.status.ListenerUtils;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.nrg.xnat.helpers.prearchive.PrearcDatabase.removePrearcVariables;

/**
 * @author Timothy R Olsen
 */
public class FinishImageUpload extends StatusProducer implements Callable<String>, StatusProducerI {
    private static final Logger logger = LoggerFactory.getLogger(FinishImageUpload.class);

    private final PrearcSession       session;
    private final URIManager.DataURIA destination;
    private final boolean             overrideExceptions, allowSessionMerge, inline;
    private final UserI user;

    public FinishImageUpload(Object control, UserI user, final PrearcSession session, final URIManager.DataURIA destination, final boolean overrideExceptions, final boolean allowSessionMerge, final boolean inline) {
        super(control);
        this.session = session;
        this.destination = destination;
        this.overrideExceptions = overrideExceptions;
        this.allowSessionMerge = allowSessionMerge;
        this.user = user;
        this.inline = inline;
    }

    @Override
    public String call() throws ActionException {
        try {
            if (isAutoArchive(session, destination)) {
                if (inline) {
                    //This is being done as part of a parent transaction and should not manage prearc cache state.
                    final Map<String, Object> sessionValues = removePrearcVariables(session.getAdditionalValues());
                    return ListenerUtils.addListeners(this, new PrearcSessionArchiver(session,
                                                                                      user,
                                                                                      sessionValues,
                                                                                      overrideExceptions,
                                                                                      allowSessionMerge,
                                                                                      false,
                                                                                      isOverwriteFiles(session)))
                                        .call();
                } else {
                    if (PrearcDatabase.setStatus(session.getFolderName(), session.getTimestamp(), session.getProject(), PrearcUtils.PrearcStatus.ARCHIVING)) {
                        boolean override = overrideExceptions, append = allowSessionMerge;

                        final SessionData sessionData = session.getSessionData();
                        if (sessionData != null) {
                            PrearchiveCode code = sessionData.getAutoArchive();
                            if (code != null) {
                                switch (code) {
                                    case Manual:
                                        override = false;
                                        append = false;
                                        break;
                                    case AutoArchive:
                                        override = false;
                                        append = true;
                                        break;
                                    case AutoArchiveOverwrite:
                                        override = false;
                                        append = true;
                                        //theoretically we could also set overwrite_files to true here.  But, that is handled by the isOverwriteFiles method which allows for other methods of specifying the value
                                        break;
                                }
                            }
                        }
                        return PrearcDatabase.archive(session, override, append, isOverwriteFiles(session), user, getListeners());
                    } else {
                        throw new ServerException("Unable to lock session for archiving.");
                    }
                }
            } else {
                populateAdditionalFields(session.getSessionDir());
                return session.getUrl();
            }

        } catch (ActionException e) {
            logger.error("", e);
            throw e;
        } catch (Exception e) {
            logger.error("", e);
            throw new ServerException(e);
        }
    }

    /**
     * This method will allow users to pass xml path as parameters.  The values supplied will be copied into the loaded session.
     */
    private void populateAdditionalFields(final File sessionDIR) throws ActionException {
        //prepare params by removing non xml path names
        final Map<String, Object> cleaned = XMLPathShortcuts.identifyUsableFields(session.getAdditionalValues(), XMLPathShortcuts.EXPERIMENT_DATA, false);

        if (cleaned.size() > 0) {
            final SAXReader reader = new SAXReader(user);
            final File xml = new File(sessionDIR.getParentFile(), sessionDIR.getName() + ".xml");

            try {
                XFTItem item = reader.parse(xml.getAbsolutePath());

                try {
                    item.setProperties(cleaned, true);
                } catch (Exception e) {
                    failed("unable to map parameters to valid xml path: " + e.getMessage());
                    throw new ClientException("unable to map parameters to valid xml path: ", e);
                }

                FileWriter fw = null;
                try {
                    fw = new FileWriter(xml);
                    item.toXML(fw, false);
                } catch (IllegalArgumentException | IOException | SAXException e) {
                    throw new ServerException(e);
                } finally {
                    try {
                        if (fw != null) {
                            fw.close();
                        }
                    } catch (IOException ignored) {
                    }
                }
            } catch (IOException | SAXException e1) {
                throw new ServerException(e1);
            }
        }
    }

    public boolean isAutoArchive() throws Exception {
        return isAutoArchive(session, destination);
    }

    public String getSource() throws Exception {
        final SessionData sessionData = session.getSessionData();
        if (sessionData != null) {
            return sessionData.getSource();
        }
        return null;
    }

    private static boolean isAutoArchive(final PrearcSession session, final URIManager.DataURIA destination) throws Exception {
        //determine auto-archive setting
        if (session.getProject() == null) {
            return setArchiveReason(session, false);
        }

        final SessionData sessionData = session.getSessionData();
        if (sessionData != null) {
            PrearchiveCode sessionAutoArcSetting = sessionData.getAutoArchive();
            if(sessionAutoArcSetting != null){
                if (sessionAutoArcSetting == PrearchiveCode.AutoArchive || sessionAutoArcSetting == PrearchiveCode.AutoArchiveOverwrite) {
                    return setArchiveReason(session, true);
                }
                else if(sessionAutoArcSetting == PrearchiveCode.Manual){
                    return setArchiveReason(session, false);
                }
            }
        }

        if (destination != null && destination instanceof URIManager.ArchiveURI) {
            setArchiveReason(session, false);
            return true;
        }

        // If the user has specified auto-archive, override the project setting.
        Boolean userArchiveSetting = isAutoArchive(session.getAdditionalValues());
        if (null != userArchiveSetting) {
            return setArchiveReason(session, userArchiveSetting);
        }

        final Integer code = ArcSpecManager.GetInstance().getPrearchiveCodeForProject(session.getProject());
        if (code != null && code >= 4) {
            return setArchiveReason(session, true);
        }

        return setArchiveReason(session, false);
    }

    public static boolean setArchiveReason(PrearcSession session, boolean autoArchive) {
        if (autoArchive) {
            if (!session.getAdditionalValues().containsKey(EventUtils.EVENT_REASON)) {
                session.getAdditionalValues().put(EventUtils.EVENT_REASON, "auto-archive");
            }
        } else {
            if (!session.getAdditionalValues().containsKey(EventUtils.EVENT_REASON)) {
                session.getAdditionalValues().put(EventUtils.EVENT_REASON, "standard upload");
            }
        }

        return autoArchive;
    }

    private static Boolean isAutoArchive(final Map<String, Object> params) {
        String aa = (String) params.get(RequestUtil.AA);
        if (aa == null) {
            aa = (String) params.get(RequestUtil.AUTO_ARCHIVE);
        }

        return (aa == null) ? null : aa.equalsIgnoreCase(RequestUtil.TRUE);
    }

    private static boolean isOverwriteFiles(final Map<String, Object> params) {
        String of = (String) params.get(RequestUtil.OVERWRITE_FILES);

        return of != null && of.equalsIgnoreCase(RequestUtil.TRUE);
    }

    private static boolean isOverwriteFiles(final PrearcSession session) throws Exception {
        //determine overwrite_files setting
        if (session.getProject() == null) {
            return false;
        }

        if (isOverwriteFiles(session.getAdditionalValues())) {
            return true;
        }

        final SessionData sessionData = session.getSessionData();
        if (sessionData != null) {
            PrearchiveCode sessionAutoArcSetting = sessionData.getAutoArchive();
            if (sessionAutoArcSetting != null && sessionAutoArcSetting == PrearchiveCode.AutoArchiveOverwrite) {
                return true;
            }
        }

        final Integer code = ArcSpecManager.GetInstance().getPrearchiveCodeForProject(session.getProject());
        return code != null && code.equals(PrearchiveCode.AutoArchiveOverwrite.getCode());

    }
}
