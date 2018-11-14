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
package org.neo4j.ogm.drivers.embedded.types;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.neo4j.ogm.driver.ParameterConversion;
import org.neo4j.ogm.driver.TypeAdapterLookupDelegate;
import org.neo4j.ogm.driver.TypeSystemBasedParameterConversion;
import org.neo4j.ogm.drivers.embedded.types.adapter.EmbeddedPointToPointAdapter;
import org.neo4j.ogm.drivers.embedded.types.adapter.PointToEmbeddedPointAdapter;
import org.neo4j.ogm.driver.TypeSystem;
import org.neo4j.ogm.types.spatial.CartesianPoint2d;
import org.neo4j.ogm.types.spatial.CartesianPoint3d;
import org.neo4j.ogm.types.spatial.GeographicPoint2d;
import org.neo4j.ogm.types.spatial.GeographicPoint3d;

/**
 * @author Michael J. Simons
 */
class EmbeddedNativeTypes implements TypeSystem {

    private final TypeAdapterLookupDelegate nativeToMappedAdapter;
    private final TypeAdapterLookupDelegate mappedToNativeAdapter;

    EmbeddedNativeTypes() {

        Map<Class<?>, Function> nativeToMappedAdapter = new HashMap<>();
        Map<Class<?>, Function> mappedToNativeAdapter = new HashMap<>();

        addSpatialFeatures(nativeToMappedAdapter, mappedToNativeAdapter);

        this.nativeToMappedAdapter = new TypeAdapterLookupDelegate(nativeToMappedAdapter);
        this.mappedToNativeAdapter = new TypeAdapterLookupDelegate(mappedToNativeAdapter);
    }

    private static void addSpatialFeatures(Map<Class<?>, Function> nativeToMappedAdapter,
        Map<Class<?>, Function> mappedToNativeAdapter) {
        Class<?> pointClass = null;
        try {
            pointClass = Class
                .forName("org.neo4j.values.storable.PointValue", false, EmbeddedNativeTypes.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }

        nativeToMappedAdapter.put(pointClass, new EmbeddedPointToPointAdapter());

        PointToEmbeddedPointAdapter pointToEmbeddedPointAdapter = new PointToEmbeddedPointAdapter();
        mappedToNativeAdapter.put(CartesianPoint2d.class, pointToEmbeddedPointAdapter);
        mappedToNativeAdapter.put(CartesianPoint3d.class, pointToEmbeddedPointAdapter);
        mappedToNativeAdapter.put(GeographicPoint2d.class, pointToEmbeddedPointAdapter);
        mappedToNativeAdapter.put(GeographicPoint3d.class, pointToEmbeddedPointAdapter);
    }

    public boolean supportsAsNativeType(Class<?> clazz) {
        return mappedToNativeAdapter.hasAdapterFor(clazz);
    }

    @Override
    public Function<Object, Object> getNativeToMappedTypeAdapter(Class<?> clazz) {
        return nativeToMappedAdapter.findAdapterFor(clazz);
    }

    @Override
    public Function<Object, Object> getMappedToNativeTypeAdapter(Class<?> clazz) {
        return mappedToNativeAdapter.findAdapterFor(clazz);
    }

    @Override
    public ParameterConversion getParameterConversion() {
        return new TypeSystemBasedParameterConversion(this);
    }
}