/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2019, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package ru.avicomp.ontapi.internal;

import org.apache.jena.graph.GraphListener;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.util.graph.GraphListenerBase;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLObject;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * An auxiliary class-container to provide
 * a common way for working with {@link OWLObject}s and {@link Triple}s all together.
 * It is logically based on the {@link ONTObject} container,
 * which is a wrapper around {@link OWLObject OWLObject}
 * with the reference to get all associated {@link Triple RDF triple}s.
 * This class is used by the {@link InternalModel Internal Model} cache as indivisible bucket.
 * <p>
 * Created by @ssz on 09.03.2019.
 *
 * @param <X> any subtype of {@link OWLObject} (in system either {@link OWLAxiom} or {@link OWLAnnotation})
 */
@SuppressWarnings("WeakerAccess")
public class ObjectTriplesMapImpl<X extends OWLObject> implements ObjectTriplesMap<X> {

    // objects provider:
    private final Supplier<Iterator<ONTObject<X>>> loader;
    // soft reference:
    private final InternalCache.Loading<ObjectTriplesMapImpl<X>, CachedMap> map;

    // a state flag that responds whether some axioms have been manually added to this map
    // the dangerous of manual added axioms is that the same information can be represented in different ways.
    private volatile boolean hasNew;

    public ObjectTriplesMapImpl(Supplier<Iterator<ONTObject<X>>> loader) {
        this.loader = Objects.requireNonNull(loader);
        this.map = InternalCache.createSoft(ObjectTriplesMapImpl::loadMap, true);
    }

    protected CachedMap loadMap() {
        this.hasNew = false;
        Iterator<ONTObject<X>> it = loader.get();
        Map<X, ONTObject<X>> res = new HashMap<>();
        while (it.hasNext()) {
            ONTObject<X> v = it.next();
            res.merge(v.getObject(), v, ONTObject::append);
        }
        return new CachedMap(res, null);
    }

    public CachedMap getMap() {
        return map.get(this);
    }

    public boolean isLoaded() {
        return !map.asCache().isEmpty();
    }

    @Override
    public void load() {
        getMap();
    }

    @Override
    public boolean hasNew() {
        return isLoaded() && hasNew;
    }

    @Override
    public Stream<X> objects() {
        return getMap().getObjects().keySet().stream();
    }

    @Override
    public Stream<Triple> triples() {
        return getMap().getTriples().keySet().stream();
    }

    @Override
    public Stream<Triple> triples(X o) throws JenaException {
        return getMap().getObjects().get(o).triples();
    }

    @Override
    public boolean contains(X o) {
        return getMap().getObjects().containsKey(o);
    }

    @Override
    public boolean contains(X o, Triple t) {
        CachedMap m;
        if (isLoaded() && (m = getMap()).hasTriplesMap()) {
            Set<X> res = m.getTriples().get(t);
            return res != null && res.contains(o);
        }
        return triples(o).anyMatch(t::equals);
    }

    @Override
    public boolean contains(Triple triple) {
        return getMap().getTriples().containsKey(triple);
    }

    /**
     * Registers the given object-triple pair into the map.
     * Note that each object, in general, is associated with many triples, not just one.
     * If a set of associated triples is incomplete the method {@link #triples(OWLObject)}
     * may throw a {@link JenaException jena exception}.
     * WARNING: Must be called only from the {@link Listener listener}.
     *
     * @param key    {@link X} (axiom or annotation)
     * @param triple {@link Triple}
     */
    public void register(X key, Triple triple) {
        this.hasNew = true;
        CachedMap map = getMap();
        map.getObjects().merge(key, new TripleSet<>(key, triple), (a, b) -> {
            if (a.isDefinitelyEmpty()) return b;
            return a.append(b);
        });
        map.getTriples().computeIfAbsent(triple, t -> new HashSet<>()).add(key);
    }

    /**
     * Unregisters the given object-triple pair from this map.
     * Both the object and the triple may still be present in the map after this operation.
     * Impl note: an {@link InternalModel} uses this method only while <b>adding</b> an object.
     * It seems now this method is almost unused by the system, although such a situation,
     * when removing triple happens on adding object may still exist,
     * it depends on Jena and other ONT-API places.
     * For deleting the method {@link #delete(OWLObject)} is used.
     * The operation may broke structure and, therefore,
     * the method {@link #triples(OWLObject)} may throw {@link JenaException Jena Exception} in this case.
     * WARNING: Must be called only the {@link Listener listener}.
     * @param key    OWLObject (axiom or annotation)
     * @param triple {@link Triple}
     */
    public void unregister(X key, Triple triple) {
        if (!isLoaded()) return;
        CachedMap map = getMap();
        Map<X, ONTObject<X>> objectsCache = map.getObjects();
        Optional.ofNullable(objectsCache.get(key)).ifPresent(v -> {
            ONTObject<X> x = v.delete(triple);
            objectsCache.put(x.getObject(), x);
            try {
                if (x.isDefinitelyEmpty() || x.triples().count() == 0) {
                    objectsCache.remove(x.getObject());
                }
            } catch (JenaException e) {
                // incomplete object
            }
        });
        if (!map.hasTriplesMap()) return;
        Map<Triple, Set<X>> triplesCache = map.getTriples();
        Optional.ofNullable(triplesCache.get(triple)).ifPresent(set -> {
            set.remove(key);
            if (set.isEmpty()) {
                triplesCache.remove(triple);
            }
        });
    }

    @Override
    public GraphListener addListener(X key) {
        return new Listener<>(this, key);
    }

    /**
     * Deletes the given object and all its associated triples.
     *
     * @param key {@link X} (axiom or annotation)
     */
    @Override
    public void delete(X key) {
        if (!isLoaded()) return;
        CachedMap map = getMap();
        ONTObject<X> res = map.getObjects().remove(key);
        if (!map.hasTriplesMap()) return;
        Map<Triple, Set<X>> triplesCache = map.getTriples();
        res.triples().forEach(t -> Optional.ofNullable(triplesCache.get(t)).ifPresent(set -> {
            set.remove(res.getObject());
            if (set.isEmpty()) {
                triplesCache.remove(t);
            }
        }));
    }

    @Override
    public void clear() {
        map.asCache().clear();
    }

    /**
     * An internal object-collection
     * that holds {@code Map} with {@link X OWLObject}-keys and {@code Map} with {@link Triple}-keys,
     * the last one as {@link java.lang.ref.SoftReference}.
     */
    protected class CachedMap {
        private final Map<X, ONTObject<X>> objectsCache;
        private final InternalCache.Loading<CachedMap, Map<Triple, Set<X>>> triplesCache;

        protected CachedMap(Map<X, ONTObject<X>> objectsCache, Map<Triple, Set<X>> triplesCache) {
            this.objectsCache = Objects.requireNonNull(objectsCache);
            this.triplesCache = InternalCache.createSoft(CachedMap::loadMap, true);
            if (triplesCache != null) {
                this.triplesCache.asCache().put(this, triplesCache);
            }
        }

        protected long size() {
            return objectsCache.size();
        }

        protected Map<X, ONTObject<X>> getObjects() {
            return objectsCache;
        }

        protected boolean hasTriplesMap() {
            return !triplesCache.asCache().isEmpty();
        }

        protected Map<Triple, Set<X>> getTriples() {
            return triplesCache.get(this);
        }

        protected Map<Triple, Set<X>> loadMap() {
            Map<Triple, Set<X>> res = new HashMap<>();
            for (ONTObject<X> v : objectsCache.values()) {
                try {
                    v.triples().forEach(t -> res.computeIfAbsent(t, x -> new HashSet<>()).add(v.getObject()));
                } catch (JenaException ex) {
                    // object has wrong state: it is being registered or unregistered
                    // ignore exception
                }
            }
            return res;
        }
    }


    /**
     * An {@link ONTObject} which holds triples in memory.
     * Used in caches.
     * Note: it is mutable object while the base is immutable.
     *
     * @param <V>
     */
    private class TripleSet<V extends X> extends ONTObject<V> {
        private final Set<Triple> triples;

        TripleSet(V object, Triple t) {
            this(object);
            this.triples.add(t);
        }

        TripleSet(V object) { // empty
            this(object, new HashSet<>());
        }

        private TripleSet(V object, Set<Triple> triples) {
            super(object);
            this.triples = triples;
        }

        @Override
        public Stream<Triple> triples() {
            return triples.stream();
        }

        @Override
        protected boolean isDefinitelyEmpty() {
            return triples.isEmpty();
        }

        @Override
        public ONTObject<V> add(Triple triple) {
            triples.add(triple);
            return this;
        }

        @Override
        public ONTObject<V> delete(Triple triple) {
            triples.remove(triple);
            return this;
        }
    }

    /**
     * A {@link GraphListenerBase Graph Listener} implementation
     * that monitors the triples addition and deletion for the specified {@link O object}.
     *
     * @param <O> a subtype of {@link OWLObject}
     */
    public static class Listener<O extends OWLObject> extends GraphListenerBase {
        private final ObjectTriplesMapImpl<O> store;
        private final O object;

        Listener(ObjectTriplesMapImpl<O> store, O object) {
            this.store = Objects.requireNonNull(store);
            this.object = Objects.requireNonNull(object);
        }

        @Override
        protected void addEvent(Triple t) {
            store.register(object, t);
        }

        @Override
        protected void deleteEvent(Triple t) {
            store.unregister(object, t);
        }
    }

}
