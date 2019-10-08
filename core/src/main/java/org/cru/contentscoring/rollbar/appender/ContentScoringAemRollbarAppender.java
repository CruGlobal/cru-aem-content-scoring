package org.cru.contentscoring.rollbar.appender;

import ch.qos.logback.core.Appender;
import com.adobe.granite.security.user.UserPropertiesService;
import org.cru.commons.rollbar.appender.AemRollbarAppender;
import org.cru.commons.service.SimpleSystemsManagementService;
import org.cru.commons.service.SystemUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(
    immediate = true,
    service = Appender.class,
    property = {
        "loggers=org.cru.contentscoring",
        "loggers=ROOT"
    }
)
public class ContentScoringAemRollbarAppender extends AemRollbarAppender {
    private static final String SYSTEM_NAME = "cru_content_scoring";
    private static final String NAMESPACE_KEY = "cru-content-scoring";

    @Reference
    private SimpleSystemsManagementService simpleSystemsManagementService;

    @Reference
    private UserPropertiesService userPropertiesService;

    @Reference
    private SystemUtils systemUtils;

    @Activate
    public void activate() {
        initialize(simpleSystemsManagementService, userPropertiesService, systemUtils);
    }

    @Override
    public String getSystemName() {
        return SYSTEM_NAME;
    }

    @Override
    public String getCustomNamespaceKey() {
        return NAMESPACE_KEY;
    }

    @Override
    public String[] getFilteredPackages() {
        return new String[] {
            "org.cru.contentscoring"
        };
    }
}
