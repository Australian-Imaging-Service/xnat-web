/*
 * web: org.nrg.xnat.services.messaging.automation.AutomatedScriptRequestListener
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnat.services.messaging.automation;

import lombok.extern.slf4j.Slf4j;
import org.nrg.automation.entities.ScriptOutput;
import org.nrg.automation.entities.ScriptOutput.Status;
import org.nrg.automation.event.AutomationCompletionEventI;
import org.nrg.automation.event.AutomationEventImplementerI;
import org.nrg.automation.services.ScriptRunnerService;
import org.nrg.framework.exceptions.NrgServiceException;
import org.nrg.xdat.security.helpers.Users;
import org.nrg.xdat.services.DataTypeAwareEventService;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xft.event.entities.WorkflowStatusEvent;
import org.nrg.xft.event.persist.PersistentWorkflowI;
import org.nrg.xft.event.persist.PersistentWorkflowUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.utils.WorkflowUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The listener interface for receiving automatedScriptRequest events.
 * The class that is interested in processing a automatedScriptRequest
 * event implements this interface, and the object created
 * with that class is registered with a component using the
 * component's <code>addAutomatedScriptRequestListener<code> method. When
 * the automatedScriptRequest event occurs, that object's appropriate
 * method is invoked.
 */
@Slf4j
public class AutomatedScriptRequestListener {
    @Autowired
    public void setScriptRunnerService(final ScriptRunnerService service) {
        _service = service;
    }

    @Autowired
    public void setEventService(final DataTypeAwareEventService eventService) {
        _eventService = eventService;
    }

    /**
     * On request.
     *
     * @param request the request
     *
     * @throws Exception the exception
     */
    public void onRequest(final AutomatedScriptRequest request) throws Exception {
        final UserI user = Users.getUser(request.getUsername());

        final PersistentWorkflowI workflow = WorkflowUtils.getUniqueWorkflow(user, request.getScriptWorkflowId());
        assert workflow != null;
        workflow.setStatus(PersistentWorkflowUtils.IN_PROGRESS);
        WorkflowUtils.save(workflow, workflow.buildEvent());

        final AutomationCompletionEventI  automationCompletionEvent = request.getAutomationCompletionEvent();
        final AutomationEventImplementerI automationEvent           = request.getAutomationEvent();

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("user", user);
        parameters.put("scriptId", request.getScriptId());
        parameters.put("event", request.getEvent());
        parameters.put("srcEventId", request.getSrcEventId());
        final String srcEventClass = request.getSrcEventClass();
        parameters.put("srcEventClass", srcEventClass);
        // For backwards compatibility
        if (srcEventClass.contains("WorkflowStatusEvent") && automationEvent instanceof WorkflowStatusEvent) {
            final WorkflowStatusEvent workflowStatusEvent = (WorkflowStatusEvent) automationEvent;
            if (workflowStatusEvent.getWorkflow() != null) {
                parameters.put("srcWorkflowId", workflowStatusEvent.getWorkflow().getWorkflowId());
            }
        } else if (srcEventClass.contains("WrkWorkflowdata")) {
            parameters.put("srcWorkflowId", request.getArgumentMap().get("wrkWorkflowId"));
        }
        if (parameters.get("srcWorkflowId") == null) {
            parameters.put("srcWorkflowId", null);
        }
        parameters.put("scriptWorkflowId", request.getScriptWorkflowId());
        parameters.put("dataType", request.getDataType());
        parameters.put("dataId", request.getDataId());
        parameters.put("externalId", request.getExternalId());
        parameters.put("workflow", workflow);
        parameters.put("arguments", request.getArgumentJson());
        if (request.getArgumentMap() != null && !request.getArgumentMap().isEmpty()) {
            parameters.putAll(request.getArgumentMap());
        }
        ScriptOutput scriptOut = null;
        try {
            scriptOut = _service.runScript(_service.getScript(request.getScriptId()), parameters);
            if (PersistentWorkflowUtils.IN_PROGRESS.equals(workflow.getStatus())) {
                WorkflowUtils.complete(workflow, workflow.buildEvent());
            }
            if (automationCompletionEvent != null && scriptOut != null) {
                automationCompletionEvent.getScriptOutputs().add(scriptOut);
            }
        } catch (NrgServiceException e) {
            final String message = String.format("Failed running the script %s by user %s for event %s on data type %s instance %s from project %s",
                                                 request.getScriptId(),
                                                 request.getUsername(),
                                                 request.getEvent(),
                                                 request.getDataType(),
                                                 request.getDataId(),
                                                 request.getExternalId());
            if (scriptOut == null) {
                scriptOut = new ScriptOutput();
                scriptOut.setStatus(Status.ERROR);
                scriptOut.setOutput(message);
            }
            AdminUtils.sendAdminEmail("Script execution failure", message);
            log.error(message, e);
            if (PersistentWorkflowUtils.IN_PROGRESS.equals(workflow.getStatus())) {
                WorkflowUtils.fail(workflow, workflow.buildEvent());
            }
        }
        if (automationCompletionEvent != null) {
            if (_eventService != null) {
                automationCompletionEvent.setEventCompletionTime(System.currentTimeMillis());
                _eventService.triggerEvent(automationCompletionEvent);
                final List<String> notifyList = automationCompletionEvent.getNotificationList();
                if (notifyList != null && !notifyList.isEmpty()) {
                    final String scriptOutStr =
                            (automationCompletionEvent.getScriptOutputs() != null && automationCompletionEvent.getScriptOutputs().size() > 0) ?
                            scriptOutputToHtmlString(automationCompletionEvent.getScriptOutputs()) :
                            "<h3>No output was returned from the script run</h3>";
                    final String EMAIL_SUBJECT = "Automation Results (" + request.getScriptId() + ")";
                    AdminUtils.sendUserHTMLEmail(EMAIL_SUBJECT, scriptOutStr, false, notifyList.toArray(new String[0]));
                }
            }
        }
    }

    /**
     * Script output to html string.
     *
     * @param scriptOutputs the script outputs
     *
     * @return the string
     */
    private String scriptOutputToHtmlString(List<ScriptOutput> scriptOutputs) {
        if (scriptOutputs == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ScriptOutput scriptOut : scriptOutputs) {
            sb.append("<br><b>SCRIPT EXECUTION RESULTS</b><br>");
            // NOTE:  Lets not report success status, because we really only know failures.  The script itself
            // may report errors, so let's let the script do status reporting when it seems to have executed successfully.
            if (!scriptOut.getStatus().equals(Status.SUCCESS)) {
                sb.append("<br><b>FINAL STATUS:  ").append(scriptOut.getStatus()).append("</b><br>");
            }
            if (scriptOut.getStatus().equals(Status.ERROR) && scriptOut.getResults() != null && scriptOut.getResults().toString().length() > 0) {
                sb.append("<br><b>SCRIPT RESULTS</b><br>");
                sb.append(scriptOut.getResults().toString().replace("\n", "<br>"));
            }
            if (scriptOut.getOutput() != null && scriptOut.getOutput().length() > 0) {
                sb.append("<br><b>SCRIPT STDOUT</b><br>");
                sb.append(scriptOut.getOutput().replace("\n", "<br>"));
            }
            if (scriptOut.getErrorOutput() != null && scriptOut.getErrorOutput().length() > 0) {
                sb.append("<br><b>SCRIPT STDERR/EXCEPTION</b><br>");
                sb.append(scriptOut.getErrorOutput().replace("\n", "<br>"));
            }
        }
        return sb.toString();
    }

    /**
     * The _service.
     */
    private ScriptRunnerService _service;

    /**
     * The _event service.
     */
    private DataTypeAwareEventService _eventService;
}
