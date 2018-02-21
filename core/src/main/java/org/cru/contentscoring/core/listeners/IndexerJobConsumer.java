package org.cru.contentscoring.core.listeners;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;

@Component
@Service(value={JobConsumer.class})
@Property(name=JobConsumer.PROPERTY_TOPICS, value=ReplicationEventHandler.SCORING_JOB_NAME)
public class IndexerJobConsumer implements JobConsumer {
   
    private Logger LOG = LoggerFactory.getLogger(IndexerJobConsumer.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ContentScoreUpdateService service;

    public JobResult process(final Job job) {
        ReplicationAction action = (ReplicationAction) job.getProperty(ReplicationEventHandler.EVENT_PARAM);

        try (ResourceResolver resourceResolver = resolverFactory.getServiceResourceResolver(null)) {
            Session session = resourceResolver.adaptTo(Session.class);
            PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            Page page = pageManager.getPage(action.getPath());

            LOG.debug("ReplicationActionType: " + action.getType());

            if (ReplicationActionType.ACTIVATE.equals(action.getType())
                    || ReplicationActionType.INTERNAL_POLL.equals(action.getType())) {

                LOG.debug("{} path={} ", action.getType(), action.getPath());

                if (page != null) {
                    service.updateContentScore(page);
                    session.save();
                }
            }
            return JobResult.OK;
        } catch (Exception e) {
            LOG.error("Failed to process incoming job: ", e);
            return JobResult.FAILED;
        }
    }

    
}
