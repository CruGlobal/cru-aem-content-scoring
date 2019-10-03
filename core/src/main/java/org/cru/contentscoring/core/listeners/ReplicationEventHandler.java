package org.cru.contentscoring.core.listeners;

import java.util.HashMap;
import java.util.Map;

import org.apache.sling.event.jobs.JobManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.replication.ReplicationAction;

/**
 * A service to listen changes in the resource tree. It registers an 
 * event handler service. When the event is handled a new Job is 
 * created to index the page related with the replication event thrown.
 */
@Component(service = EventHandler.class, immediate = true, property = {
        "event.topics=" + ReplicationAction.EVENT_TOPIC})
public class ReplicationEventHandler implements EventHandler {

    private Logger LOG = LoggerFactory.getLogger(this.getClass());
    static final String EVENT_PARAM = "action";
    static final String SCORING_JOB_NAME = "org/cru/content-scoring-update";

    @Reference
    private JobManager jobManager;

    public void handleEvent(Event event) {
        LOG.debug("Handle replication event.");
        ReplicationAction action = ReplicationAction.fromEvent(event);
        Map<String, Object> jobProperties = new HashMap<>();
        jobProperties.put(EVENT_PARAM, action);
        jobManager.addJob(SCORING_JOB_NAME, jobProperties);
    }
}
