package org.nrg.xnat.eventservice.events;


import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.event.XnatEventServiceEvent;
import org.nrg.xdat.model.XnatSubjectassessordataI;
import org.nrg.xnat.eventservice.listeners.EventServiceListener;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@XnatEventServiceEvent(name="NonImageSubjectAssessorEvent")
public class NonImageSubjectAssessorEvent extends CombinedEventServiceEvent<NonImageSubjectAssessorEvent, XnatSubjectassessordataI> {

    public enum Status {CREATED, DELETED};

    public NonImageSubjectAssessorEvent(){};

    public NonImageSubjectAssessorEvent(final XnatSubjectassessordataI payload, final String eventUser, final Status status, final String projectId) {
        super(payload, eventUser, status, projectId, (payload != null ? payload.getXSIType() : null));
    }

    @Override
    public String getDisplayName() {
        return "Non-imaging Subject Assessor Event";
    }

    @Override
    public String getDescription() {
        return "Non-imaging Subject Assessor created or deleted.";
    }

    @Override
    public Boolean isPayloadXsiType() {
        return true;
    }

    @Override
    public List<String> getStatiStates() { return Arrays.stream(Status.values()).map(Status::name).collect(Collectors.toList()); }

    @Override
    public EventServiceListener getInstance() {
        return new NonImageSubjectAssessorEvent();
    }
}
