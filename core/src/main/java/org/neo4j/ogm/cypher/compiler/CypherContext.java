/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with
 * separate copyright notices and license terms. Your use of the source
 * code for these subcomponents is subject to the terms and
 *  conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package org.neo4j.ogm.cypher.compiler;

import static java.util.Collections.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.compiler.SrcTargetKey;
import org.neo4j.ogm.context.Mappable;

/**
 * Maintains contextual information throughout the process of compiling Cypher statements to persist a graph of objects.
 *
 * @author Mark Angrish
 * @author Vince Bickers
 * @author Luanne Misquitta
 */
public class CypherContext implements CompileContext {

    private final Map<Object, NodeBuilderHorizonPair> visitedObjects = new IdentityHashMap<>();
    private final Set<Long> visitedRelationshipEntities = new HashSet<>();

    private final Map<Long, Object> createdObjectsWithId = new HashMap<>();
    private final Collection<Mappable> registeredRelationships = new HashSet<>();
    private final Collection<Mappable> deletedRelationships = new HashSet<>();
    private final Map<Long, Long> newNodeIds = new HashMap<>();

    private final Collection<Object> log = new HashSet<>();
    private final Map<SrcTargetKey, Collection<Object>> transientRelsIndex = new HashMap<>();

    private final Compiler compiler;

    public CypherContext(Compiler compiler) {
        this.compiler = compiler;
    }

    public boolean visited(Object entity, int horizon) {
        NodeBuilderHorizonPair pair = visitedObjects.get(entity);
        return pair != null && pair.getHorizon() > horizon;
    }

    @Override
    public void visit(Object entity, NodeBuilder nodeBuilder, int horizon) {
        this.visitedObjects.put(entity, new NodeBuilderHorizonPair(nodeBuilder, horizon));
    }

    public void registerRelationship(Mappable mappedRelationship) {
        this.registeredRelationships.add(mappedRelationship);
    }

    public boolean removeRegisteredRelationship(Mappable mappedRelationship) {
        return this.registeredRelationships.remove(mappedRelationship);
    }

    @Override
    public NodeBuilder visitedNode(Object entity) {
        NodeBuilderHorizonPair pair = this.visitedObjects.get(entity);
        return pair != null ? pair.getNodeBuilder() : null;
    }

    @Override
    public void registerNewObject(Long reference, Object entity) {
        createdObjectsWithId.put(reference, entity);
        register(entity);
    }

    @Override
    public Object getNewObject(Long id) {
        return createdObjectsWithId.get(id);
    }

    public void register(Object object) {
        if (!log.contains(object)) {
            log.add(object);
        }
    }

    @Override
    public void registerTransientRelationship(SrcTargetKey key, Object object) {
        if (!log.contains(object)) {
            log.add(object);
            Collection<Object> collection = transientRelsIndex.computeIfAbsent(key, k -> new HashSet<>());
            collection.add(object);
        }
    }

    public Collection<Object> registry() {
        return log;
    }

    /**
     * Invoked when the mapper wishes to mark a set of outgoing relationships to a specific type like (a)-[:T]-&gt;(*) as deleted, prior
     * to possibly re-establishing them individually as it traverses the entity graph.
     * There are two reasons why a set of relationships might not be be able to be marked deleted:
     * 1) the request to mark them as deleted has already been made
     * 2) the relationship is not persisted in the graph (i.e. its a new relationship)
     * Only case 1) is considered to be a failed request, because this context is only concerned about
     * pre-existing relationships in the graph. In order to distinguish between the two cases, we
     * also maintain a list of successfully deleted relationships, so that if we try to delete an already-deleted
     * set of relationships we can signal the error and undelete it.
     *
     * @param src              the identity of the node at the start of the relationship
     * @param relationshipType the type of the relationship
     * @param endNodeType      the class type of the entity at the end of the relationship
     * @return true if the relationship was deleted or doesn't exist in the graph, false otherwise
     */
    public boolean deregisterOutgoingRelationships(Long src, String relationshipType, Class endNodeType) {
        return deregisterRelationships(src, true, relationshipType, endNodeType, true);
    }

    /**
     * Invoked when the mapper wishes to mark a set of incoming relationships to a specific type like (a)&lt;-[:T]-(*) as deleted, prior
     * to possibly re-establishing them individually as it traverses the entity graph.
     * There are two reasons why a set of relationships might not be be able to be marked deleted:
     * 1) the request to mark them as deleted has already been made
     * 2) the relationship is not persisted in the graph (i.e. its a new relationship)
     * Only case 1) is considered to be a failed request, because this context is only concerned about
     * pre-existing relationships in the graph. In order to distinguish between the two cases, we
     * also maintain a list of successfully deleted relationships, so that ff we try to delete an already-deleted
     * set of relationships we can signal the error and undelete it.
     *
     * @param tgt                the identity of the node at the pointy end of the relationship
     * @param relationshipType   the type of the relationship
     * @param endNodeType        the class type of the entity at the other end of the relationship
     * @param relationshipEntity true if the entity is mapped via {@link RelationshipEntity}
     * @return true if the relationship was deleted or doesn't exist in the graph, false otherwise
     */
    public boolean deregisterIncomingRelationships(Long tgt, String relationshipType, Class endNodeType,
        boolean relationshipEntity) {
        return deregisterRelationships(tgt, false, relationshipType, endNodeType, relationshipEntity);
    }

    /**
     * @param entityId           the identity of the node at the pointy end of the relationship
     * @param entityIsStartNode  true is entityId is the start node of a relationship
     * @param relationshipType   the type of the relationship
     * @param endNodeType        the class type of the entity at the other end of the relationship
     * @param relationshipEntity true if the entity is mapped via {@link RelationshipEntity}
     * @return true if the relationship was deleted or doesn't exist in the graph, false otherwise
     */
    private boolean deregisterRelationships(Long entityId, boolean entityIsStartNode, String relationshipType,
        Class endNodeType, boolean relationshipEntity) {
        Iterator<Mappable> iterator = registeredRelationships.iterator();
        List<Mappable> boundForDeletion = null;
        boolean nothingToDelete = true;
        while (iterator.hasNext()) {
            Mappable mappedRelationship = iterator.next();
            long mappedNodeId = entityIsStartNode ?
                mappedRelationship.getStartNodeId() :
                mappedRelationship.getEndNodeId();
            if (mappedNodeId == entityId &&
                mappedRelationship.getRelationshipType().equals(relationshipType) &&
                endNodeType.equals(
                    relationshipEntity ? mappedRelationship.getEndNodeType() : mappedRelationship.getStartNodeType())) {
                nothingToDelete = false;
                if (isAlreadyDeleted(mappedRelationship)) {
                    continue;
                }
                if (boundForDeletion == null) {
                    boundForDeletion = new ArrayList<>();
                }
                boundForDeletion.add(mappedRelationship);
                iterator.remove();
            }
        }
        if (nothingToDelete) {
            return true; //relationships not in the graph, okay, we can return
        }
        if (boundForDeletion == null || boundForDeletion.isEmpty()) {
            return false;
        }

        this.deletedRelationships.addAll(boundForDeletion);
        return true;
    }

    public void visitRelationshipEntity(Long relationshipEntity) {
        visitedRelationshipEntities.add(relationshipEntity);
    }

    public boolean visitedRelationshipEntity(Long relationshipEntity) {
        return visitedRelationshipEntities.contains(relationshipEntity);
    }

    public Compiler getCompiler() {
        return compiler;
    }

    public Long getId(Long reference) {
        if (newNodeIds.containsKey(reference)) {
            return newNodeIds.get(reference);
        }
        return reference;
    }

    @Override
    public void registerNewId(Long reference, Long id) {
        newNodeIds.put(reference, id);
    }

    @Override
    public void deregister(NodeBuilder nodeBuilder) {
        compiler.unmap(nodeBuilder);
    }

    @Override
    public Collection<Mappable> getDeletedRelationships() {
        return deletedRelationships;
    }

    @Override
    public Object getVisitedObject(Long reference) {
        return visitedObjects.get(reference);
    }

    @Override
    public Collection<Object> getTransientRelationships(SrcTargetKey srcTargetKey) {
        Collection<Object> objects = transientRelsIndex.get(srcTargetKey);
        if (objects != null) {
            return objects;
        } else {
            return emptySet();
        }
    }

    /**
     * Checks whether a mapped relationship is already marked as deleted. This is the case when one of the relationships
     * marked as deleted contains a relationship starting and ending at the same nodes having the same relationship type.
     *
     * @param mappedRelationship The relationship that should be checked for being already marked as deleted or not
     * @return True if {@code mappedRelationship} was already marked as deleted
     */
    private boolean isAlreadyDeleted(Mappable mappedRelationship) {
        Predicate<Mappable> describesSameRelationship = r -> r.getStartNodeId() == mappedRelationship.getStartNodeId()
            && r.getEndNodeId() == mappedRelationship.getEndNodeId()
            && r.getRelationshipType().equals(mappedRelationship.getRelationshipType());

        return deletedRelationships.stream().anyMatch(describesSameRelationship);
    }

    private static class NodeBuilderHorizonPair {

        private final NodeBuilder nodeBuilder;
        private final int horizon;

        private NodeBuilderHorizonPair(NodeBuilder nodeBuilder, int horizon) {
            this.nodeBuilder = nodeBuilder;
            this.horizon = horizon;
        }

        public NodeBuilder getNodeBuilder() {
            return nodeBuilder;
        }

        public int getHorizon() {
            return horizon;
        }
    }
}
