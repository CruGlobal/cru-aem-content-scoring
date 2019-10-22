package org.cru.contentscoring.core.util;

import org.apache.sling.api.resource.Resource;

public class ExperienceFragmentUtil {
    public static final String XF_TYPE = "cq/experience-fragments/components/experiencefragment";
    public static final String XF_VARIANT_TYPE = "cq:xfVariantType";

    private ExperienceFragmentUtil() {}

    public static boolean isExperienceFragment(final Resource jcrContent) {
        return jcrContent != null && jcrContent.getResourceType().equals(XF_TYPE);
    }

    public static boolean isExperienceFragmentVariation(final Resource jcrContent) {
        return jcrContent != null && jcrContent.getValueMap().containsKey(XF_VARIANT_TYPE);
    }
}
