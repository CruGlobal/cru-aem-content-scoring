package org.cru.contentscoring.core.listeners;

import javax.jcr.Session;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.apache.sling.settings.SlingSettingsService;
import org.cru.commons.event.service.impl.ReplicationListenerOnPublishServiceImpl;
import org.cru.contentscoring.core.service.ContentScoreUpdateService;
//import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.day.cq.replication.ReplicationAction;
import com.day.cq.replication.ReplicationActionType;
import com.day.cq.wcm.api.Page;
import com.day.cq.wcm.api.PageManager;

//@Component(
//    service = JobConsumer.class,
//    immediate = true,
//    property = {
//        JobConsumer.PROPERTY_TOPICS + "=" + ReplicationListenerOnPublishServiceImpl.SCORING_JOB_NAME})
public class IndexerJobConsumer implements JobConsumer {
   
    private static final Logger LOG = LoggerFactory.getLogger(IndexerJobConsumer.class);

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory resolverFactory;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private ContentScoreUpdateService contentScoreUpdateService;

    @Reference(policyOption = ReferencePolicyOption.GREEDY)
    private SlingSettingsService slingSettingsService;

    public JobResult process(final Job job) {
        if (!slingSettingsService.getRunModes().contains("author")) {
            LOG.debug("Not on the author environment, skipping");
            return JobResult.CANCEL;
        }

        ReplicationAction action =
            (ReplicationAction) job.getProperty(ReplicationListenerOnPublishServiceImpl.EVENT_PARAM);
        LOG.debug("Processing content scoring job on: {}", action.getPath());

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

                    contentScoreUpdateService.updateContentScore(page);
                    session.save();
                }
            } else {
                LOG.debug("Page was null for {}", action.getPath());
            }
            return JobResult.OK;
        } catch (Exception e) {
            LOG.error("Failed to process incoming job: ", e);
            return JobResult.FAILED;
        }
    }

    
}
