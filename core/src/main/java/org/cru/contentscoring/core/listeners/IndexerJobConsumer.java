package org.cru.contentscoring.core.listeners;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.apache.sling.settings.SlingSettingsService;
import org.cru.commons.event.service.impl.ReplicationListenerOnPublishServiceImpl;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

@Component(service = JobConsumer.class, property = {
        JobConsumer.PROPERTY_TOPICS + "=" + ReplicationListenerOnPublishServiceImpl.SCORING_JOB_NAME})
public class IndexerJobConsumer implements JobConsumer {
   
    private Logger LOG = LoggerFactory.getLogger(IndexerJobConsumer.class);

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private ContentScoreUpdateService service;

    @Reference
    private SlingSettingsService slingSettingsService;

    public JobResult process(final Job job) {
        if (!slingSettingsService.getRunModes().contains("author")) {
            return JobResult.CANCEL;
        }

        ReplicationAction action =
            (ReplicationAction) job.getProperty(ReplicationListenerOnPublishServiceImpl.EVENT_PARAM);

        try (ResourceResolver resourceResolver = resolverFactory.getServiceResourceResolver(null)) {
            Session session = resourceResolver.adaptTo(Session.class);
            PageManager pageManager = resourceResolver.adaptTo(PageManager.class);
            Page page = pageManager.getPage(action.getPath());

            if (page != null) {
                LOG.debug("ReplicationActionType: " + action.getType());

                if (ReplicationActionType.ACTIVATE.equals(action.getType())
                        || ReplicationActionType.INTERNAL_POLL.equals(action.getType()))
                {

                    LOG.debug("{} path={} ", action.getType(), action.getPath());

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
