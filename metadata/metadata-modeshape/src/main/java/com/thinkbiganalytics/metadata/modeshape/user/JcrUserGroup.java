/**
 * 
 */
package com.thinkbiganalytics.metadata.modeshape.user;

import java.io.Serializable;
import java.security.Principal;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nonnull;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.commons.lang3.reflect.ConstructorUtils;

import com.thinkbiganalytics.metadata.api.user.User;
import com.thinkbiganalytics.metadata.api.user.UserGroup;
import com.thinkbiganalytics.metadata.modeshape.MetadataRepositoryException;
import com.thinkbiganalytics.metadata.modeshape.common.AbstractJcrAuditableSystemEntity;
import com.thinkbiganalytics.metadata.modeshape.common.JcrEntity;
import com.thinkbiganalytics.metadata.modeshape.support.JcrPropertyUtil;
import com.thinkbiganalytics.metadata.modeshape.support.JcrUtil;
import com.thinkbiganalytics.security.GroupPrincipal;

/**
 *
 * @author Sean Felten
 */
public class JcrUserGroup extends AbstractJcrAuditableSystemEntity implements UserGroup {

    /** JCR node type for users */
    public static final String NODE_TYPE = "tba:userGroup";

    /** The groups property from the mixin tba:userGroupable */
    public static final String GROUPS = "tba:groups";

    /** Name of the {@code enabled} property */
    private static final String ENABLED = "tba:enabled";

    /**
     * @param node
     */
    public JcrUserGroup(Node node) {
        super(node);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.extension.ExtensibleEntity#getId()
     */
    @Override
    public UserGroupId getId() {
        try {
            return new UserGroupId(this.node.getIdentifier());
        } catch (RepositoryException e) {
            throw new MetadataRepositoryException("Failed to retrieve the entity id", e);
        }
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.modeshape.common.AbstractJcrSystemEntity#getSystemName()
     */
    @Override
    public String getSystemName() {
        return JcrPropertyUtil.getName(this.node);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#isEnabled()
     */
    @Override
    public boolean isEnabled() {
        return getProperty(ENABLED, true);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#setEnabled(boolean)
     */
    @Override
    public void setEnabled(final boolean enabled) {
        setProperty(ENABLED, enabled);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getUsers()
     */
    @Override
    public Iterable<User> getUsers() {
        return iterateReferances(JcrUser.NODE_TYPE, User.class, JcrUser.class);
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#streamAllUsers()
     */
    public Stream<User> streamAllUsers() {
        return streamAllGroups().flatMap(g -> StreamSupport.stream(g.getUsers().spliterator(), false));
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#addUser(com.thinkbiganalytics.metadata.api.user.User)
     */
    @Override
    public boolean addUser(User user) {
        JcrUser jcrUser = (JcrUser) user;
        return JcrPropertyUtil.addToSetProperty(jcrUser.getNode(), GROUPS, this.node, true);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#removeUser(com.thinkbiganalytics.metadata.api.user.User)
     */
    @Override
    public boolean removeUser(User user) {
        JcrUser jcrUser = (JcrUser) user;
        return JcrPropertyUtil.removeFromSetProperty(jcrUser.getNode(), GROUPS, this.node);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getContainingGroups()
     */
    @Override
    public Set<UserGroup> getContainingGroups() {
        return streamContainingGroupNodes(this.node)
                        .map(node -> (UserGroup) JcrUtil.toJcrObject(node, JcrUserGroup.NODE_TYPE, JcrUserGroup.class))
                        .collect(Collectors.toSet());
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getAllContainingGroups()
     */
    @Override
    public Set<UserGroup> getAllContainingGroups() {
        return streamAllContainingGroupNodes(this.node)
                        .map(node -> JcrUtil.toJcrObject(node, JcrUserGroup.NODE_TYPE, JcrUserGroup.class))
                        .collect(Collectors.toSet());
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getGroups()
     */
    @Override
    public Iterable<UserGroup> getGroups() {
        return iterateReferances(JcrUserGroup.NODE_TYPE, UserGroup.class, JcrUserGroup.class);
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#streamAllGroups()
     */
    public Stream<UserGroup> streamAllGroups() {
        return StreamSupport.stream(getGroups().spliterator(), false).flatMap(g -> g.streamAllGroups());
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#addGroup(com.thinkbiganalytics.metadata.api.user.UserGroup)
     */
    @Override
    public boolean addGroup(UserGroup group) {
        JcrUserGroup jcrGrp = (JcrUserGroup) group;
        return JcrPropertyUtil.addToSetProperty(jcrGrp.getNode(), GROUPS, this.node, true);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#removeGroup(com.thinkbiganalytics.metadata.api.user.UserGroup)
     */
    @Override
    public boolean removeGroup(UserGroup group) {
        JcrUserGroup jcrGrp = (JcrUserGroup) group;
        return JcrPropertyUtil.removeFromSetProperty(jcrGrp.getNode(), GROUPS, this.node);
    }
    
    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getPrincial()
     */
    @Override
    public GroupPrincipal getPrincial() {
        Set<Principal> members = StreamSupport.stream(getGroups().spliterator(), false)
                        .map(g -> g.getPrincial())
                        .collect(Collectors.toSet());
        
        return new GroupPrincipal(getSystemName(), members);
    }

    /* (non-Javadoc)
     * @see com.thinkbiganalytics.metadata.api.user.UserGroup#getRootPrincial()
     */
    @Override
    public GroupPrincipal getRootPrincial() {
        return new GroupPrincipal(getSystemName());
    }
    
    private <C, J> Iterable<C> iterateReferances(String nodeType, Class<C> modelClass, Class<J> jcrClass) {
        return () -> {
            @SuppressWarnings("unchecked")
            Iterable<Property> propItr = () -> { 
                try {
                    return (Iterator<Property>) this.node.getWeakReferences();
                } catch (Exception e) {
                    throw new MetadataRepositoryException("Failed to retrieve the users in the group node: " + this.node, e);
                }
            };
            
            return StreamSupport.stream(propItr.spliterator(), false)
                            .map(p -> JcrPropertyUtil.getParent(p))
                            .filter(n -> JcrUtil.isNodeType(n, nodeType))
                            .map(n -> { 
                                try {
                                    @SuppressWarnings("unchecked")
                                    C entity = (C) ConstructorUtils.invokeConstructor(jcrClass, n);
                                    return entity;
                                } catch (Exception e) {
                                    throw new MetadataRepositoryException("Failed to retrieve create entity: " + jcrClass, e);
                                }
                            })
                            .iterator();
        };
    }
    
    private Stream<Node> streamContainingGroupNodes(Node groupNode) {
        return JcrPropertyUtil.<Node>getSetProperty(groupNode, JcrUserGroup.GROUPS).stream();
    }

    private Stream<Node> streamAllContainingGroupNodes(Node groupNode) {
        Set<Node> referenced = JcrPropertyUtil.<Node>getSetProperty(groupNode, JcrUserGroup.GROUPS);
        
        return Stream.concat(referenced.stream(), 
                             referenced.stream().flatMap(node -> streamAllContainingGroupNodes(node)));
    }
    

    /**
     * A {@link com.thinkbiganalytics.metadata.api.user.UserGroup.ID} implementation identifying a UserGroup.
     */
    static class UserGroupId extends JcrEntity.EntityId implements UserGroup.ID {

        private static final long serialVersionUID = 1L;

        UserGroupId(@Nonnull final Serializable ser) {
            super(ser);
        }
    }


}