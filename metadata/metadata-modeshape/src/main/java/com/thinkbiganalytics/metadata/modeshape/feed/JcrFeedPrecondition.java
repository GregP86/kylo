/**
 * 
 */
package com.thinkbiganalytics.metadata.modeshape.feed;

import com.thinkbiganalytics.metadata.api.feed.Feed;
import com.thinkbiganalytics.metadata.api.feed.FeedPrecondition;
import com.thinkbiganalytics.metadata.sla.api.ServiceLevelAgreement;

/**
 *
 * @author Sean Felten
 */
public class JcrFeedPrecondition implements FeedPrecondition {

    /**
     * 
     */
    public JcrFeedPrecondition() {
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.feed.FeedPrecondition#getFeed()
     */
    @Override
    public Feed<?> getFeed() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.feed.FeedPrecondition#getAgreement()
     */
    @Override
    public ServiceLevelAgreement getAgreement() {
        // TODO Auto-generated method stub
        return null;
    }

}