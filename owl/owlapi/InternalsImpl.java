/*
 * This file is part of the OWL API.
 *
 * The contents of this file are subject to the LGPL License, Version 3.0.
 *
 * Copyright (C) 2011, The University of Manchester
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0
 * in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 *
 * Copyright 2011, University of Manchester
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.manchester.cs.owl.owlapi;

import static org.semanticweb.owlapi.model.AxiomType.AXIOM_TYPES;
import static org.semanticweb.owlapi.util.CollectionFactory.createSet;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLAnonymousIndividual;
import org.semanticweb.owlapi.model.OWLAsymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLAxiomVisitor;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLDataPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLDatatypeDefinitionAxiom;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDifferentIndividualsAxiom;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLDisjointUnionAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentDataPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalDataPropertyAxiom;
import org.semanticweb.owlapi.model.OWLFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLHasKeyAxiom;
import org.semanticweb.owlapi.model.OWLImportsDeclaration;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLInverseFunctionalObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLInverseObjectPropertiesAxiom;
import org.semanticweb.owlapi.model.OWLIrreflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLLogicalAxiom;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNegativeDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLNegativeObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyDomainAxiom;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectPropertyRangeAxiom;
import org.semanticweb.owlapi.model.OWLReflexiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLSameIndividualAxiom;
import org.semanticweb.owlapi.model.OWLSubAnnotationPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.model.OWLSubDataPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubObjectPropertyOfAxiom;
import org.semanticweb.owlapi.model.OWLSubPropertyChainOfAxiom;
import org.semanticweb.owlapi.model.OWLSymmetricObjectPropertyAxiom;
import org.semanticweb.owlapi.model.OWLTransitiveObjectPropertyAxiom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.util.CollectionFactory;
import org.semanticweb.owlapi.util.OWLAxiomSearchFilter;

@SuppressWarnings("javadoc")
public class InternalsImpl extends AbstractInternalsImpl {

    private static final long serialVersionUID = 30402L;

    protected class SetPointer<K> implements Internals.SimplePointer<K>, Serializable {

        private static final long serialVersionUID = 30402L;
        private final Set<K> set;

        public SetPointer(Set<K> s) {
            set = s;
        }

        public boolean isEmpty() {
            return set.isEmpty();
        }

        public Set<K> copy() {
            return CollectionFactory.getCopyOnRequestSetFromMutableCollection(set);
        }

        public boolean add(K k) {
            return set.add(k);
        }

        public boolean contains(K k) {
            return set.contains(k);
        }

        public boolean remove(K k) {
            return set.remove(k);
        }
    }

    protected <K> SetPointer<K> buildSet() {
        return new SetPointer<K>(CollectionFactory.<K> createSet());
    }

    protected final SetPointer<OWLImportsDeclaration> importsDeclarations = buildSet();
    protected final SetPointer<OWLAnnotation> ontologyAnnotations = buildSet();
    protected final SetPointer<OWLClassAxiom> generalClassAxioms = buildSet();
    protected final SetPointer<OWLSubPropertyChainOfAxiom> propertyChainSubPropertyAxioms = buildSet();
    protected final MapPointer<AxiomType<?>, OWLAxiom> axiomsByType = build();
    protected final MapPointer<OWLClass, OWLAxiom> owlClassReferences = build();
    protected final MapPointer<OWLObjectProperty, OWLAxiom> owlObjectPropertyReferences = build();
    protected final MapPointer<OWLDataProperty, OWLAxiom> owlDataPropertyReferences = build();
    protected final MapPointer<OWLNamedIndividual, OWLAxiom> owlIndividualReferences = build();
    protected final MapPointer<OWLAnonymousIndividual, OWLAxiom> owlAnonymousIndividualReferences = build();
    protected final MapPointer<OWLDatatype, OWLAxiom> owlDatatypeReferences = build();
    protected final MapPointer<OWLAnnotationProperty, OWLAxiom> owlAnnotationPropertyReferences = build();
    protected final MapPointer<OWLEntity, OWLDeclarationAxiom> declarationsByEntity = build();

    public <K, V extends OWLAxiom> Set<K> getKeyset(Pointer<K, V> pointer) {
        final MapPointer<K, V> mapPointer = (MapPointer<K, V>) pointer;
        mapPointer.init();
        return mapPointer.keySet();
    }

    public <K, V extends OWLAxiom> Set<V> getValues(Pointer<K, V> pointer, K key) {
        final MapPointer<K, V> mapPointer = (MapPointer<K, V>) pointer;
        mapPointer.init();
        return mapPointer.getValues(key);
    }

    public <K, V extends OWLAxiom> boolean hasValues(Pointer<K, V> pointer, K key) {
        final MapPointer<K, V> mapPointer = (MapPointer<K, V>) pointer;
        mapPointer.init();
        return mapPointer.hasValues(key);
    }

    public <K, V extends OWLAxiom> boolean remove(Internals.Pointer<K, V> pointer, K k,
            V v) {
        final MapPointer<K, V> mapPointer = (MapPointer<K, V>) pointer;
        if (!mapPointer.isInitialized()) {
            return false;
        }
        return mapPointer.remove(k, v);
    }

    private final AddAxiomVisitor addChangeVisitor = new AddAxiomVisitor();
    private final RemoveAxiomVisitor removeChangeVisitor = new RemoveAxiomVisitor();

    public boolean addAxiom(final OWLAxiom axiom) {
        if (add(getAxiomsByType(), axiom.getAxiomType(), axiom)) {
            axiom.accept(addChangeVisitor);
            axiom.accept(new AbstractEntityRegistrationManager() {
                public void visit(OWLClass owlClass) {
                    add(getOwlClassReferences(), owlClass, axiom);
                }

                public void visit(OWLObjectProperty property) {
                    add(getOwlObjectPropertyReferences(), property, axiom);
                }

                public void visit(OWLDataProperty property) {
                    add(getOwlDataPropertyReferences(), property, axiom);
                }

                public void visit(OWLNamedIndividual owlIndividual) {
                    add(getOwlIndividualReferences(), owlIndividual, axiom);
                }

                public void visit(OWLAnnotationProperty property) {
                    add(getOwlAnnotationPropertyReferences(), property, axiom);
                }

                public void visit(OWLDatatype datatype) {
                    add(getOwlDatatypeReferences(), datatype, axiom);
                }

                public void visit(OWLAnonymousIndividual individual) {
                    add(getOwlAnonymousIndividualReferences(), individual, axiom);
                }
            });
            return true;
        }
        return false;
    }

    public boolean removeAxiom(final OWLAxiom axiom) {
        if (remove(getAxiomsByType(), axiom.getAxiomType(), axiom)) {
            axiom.accept(removeChangeVisitor);
            AbstractEntityRegistrationManager referenceRemover = new AbstractEntityRegistrationManager() {
                public void visit(OWLClass owlClass) {
                    remove(getOwlClassReferences(), owlClass, axiom);
                }

                public void visit(OWLObjectProperty property) {
                    remove(getOwlObjectPropertyReferences(), property, axiom);
                }

                public void visit(OWLDataProperty property) {
                    remove(getOwlDataPropertyReferences(), property, axiom);
                }

                public void visit(OWLNamedIndividual owlIndividual) {
                    remove(getOwlIndividualReferences(), owlIndividual, axiom);
                }

                public void visit(OWLAnnotationProperty property) {
                    remove(getOwlAnnotationPropertyReferences(), property, axiom);
                }

                public void visit(OWLDatatype datatype) {
                    remove(getOwlDatatypeReferences(), datatype, axiom);
                }

                public void visit(OWLAnonymousIndividual individual) {
                    remove(getOwlAnonymousIndividualReferences(), individual, axiom);
                }
            };
            axiom.accept(referenceRemover);
            return true;
        }
        return false;
    }

    public boolean isDeclared(OWLDeclarationAxiom ax) {
        return declarationsByEntity.containsKey(ax.getEntity());
    }

    public boolean isEmpty() {
        return axiomsByType.size() == 0 && ontologyAnnotations.isEmpty();
    }

    @SuppressWarnings("unchecked")
    public <T extends OWLAxiom, K> Set<T> filterAxioms(OWLAxiomSearchFilter<T, K> filter,
            K key) {
        Set<T> toReturn = createSet();
        for (T t : (Set<T>) getValues(getAxiomsByType(), filter.getAxiomType())) {
            if (filter.pass(t, key)) {
                toReturn.add(t);
            }
        }
        return toReturn;
    }

    public Set<OWLImportsDeclaration> getImportsDeclarations() {
        return importsDeclarations.copy();
    }

    public boolean addImportsDeclaration(OWLImportsDeclaration importDeclaration) {
        if (importsDeclarations.contains(importDeclaration)) {
            return false;
        }
        importsDeclarations.add(importDeclaration);
        return true;
    }

    public boolean removeImportsDeclaration(OWLImportsDeclaration importDeclaration) {
        if (!importsDeclarations.contains(importDeclaration)) {
            return false;
        }
        importsDeclarations.remove(importDeclaration);
        return true;
    }

    public Set<OWLAnnotation> getOntologyAnnotations() {
        return ontologyAnnotations.copy();
    }

    public boolean addOntologyAnnotation(OWLAnnotation ann) {
        return ontologyAnnotations.add(ann);
    }

    public boolean removeOntologyAnnotation(OWLAnnotation ann) {
        return ontologyAnnotations.remove(ann);
    }

    public <K, V extends OWLAxiom> boolean contains(Pointer<K, V> p, K k) {
        return ((MapPointer<K, V>) p).containsKey(k);
    }

    public <K, V extends OWLAxiom> boolean contains(Pointer<K, V> p, K k, V v) {
        return ((MapPointer<K, V>) p).contains(k, v);
    }

    public int getAxiomCount() {
        return axiomsByType.size();
    }

    public Set<OWLAxiom> getAxioms() {
        return axiomsByType.getAllValues();
    }

    public <T extends OWLAxiom> int getAxiomCount(AxiomType<T> axiomType) {
        if (!axiomsByType.isInitialized()) {
            return 0;
        }
        final Collection<OWLAxiom> collection = axiomsByType.getValues(axiomType);
        if (collection.isEmpty()) {
            return 0;
        }
        Set<OWLAxiom> axioms = new HashSet<OWLAxiom>(collection);
        return axioms.size();
    }

    public Set<OWLLogicalAxiom> getLogicalAxioms() {
        Set<OWLLogicalAxiom> axioms = createSet();
        for (AxiomType<?> type : AXIOM_TYPES) {
            if (type.isLogical()) {
                Collection<OWLAxiom> axiomSet = axiomsByType.getValues(type);
                if (axiomSet != null) {
                    for (OWLAxiom ax : axiomSet) {
                        axioms.add((OWLLogicalAxiom) ax);
                    }
                }
            }
        }
        return axioms;
    }

    public int getLogicalAxiomCount() {
        return getLogicalAxioms().size();
    }

    public <K, V extends OWLAxiom> boolean add(Pointer<K, V> p, K k, V v) {
        MapPointer<K, V> map = (MapPointer<K, V>) p;
        if (!map.isInitialized()) {
            return false;
        }
        return map.put(k, v);
    }

    public Set<OWLClassAxiom> getGeneralClassAxioms() {
        return generalClassAxioms.copy();
    }

    public void addGeneralClassAxioms(OWLClassAxiom ax) {
        generalClassAxioms.add(ax);
    }

    public void removeGeneralClassAxioms(OWLClassAxiom ax) {
        generalClassAxioms.remove(ax);
    }

    public void addPropertyChainSubPropertyAxioms(OWLSubPropertyChainOfAxiom ax) {
        propertyChainSubPropertyAxioms.add(ax);
    }

    public void removePropertyChainSubPropertyAxioms(OWLSubPropertyChainOfAxiom ax) {
        propertyChainSubPropertyAxioms.remove(ax);
    }

    public MapPointer<OWLClass, OWLAxiom> getOwlClassReferences() {
        return owlClassReferences;
    }

    public MapPointer<OWLObjectProperty, OWLAxiom> getOwlObjectPropertyReferences() {
        return owlObjectPropertyReferences;
    }

    public MapPointer<OWLDataProperty, OWLAxiom> getOwlDataPropertyReferences() {
        return owlDataPropertyReferences;
    }

    public MapPointer<OWLNamedIndividual, OWLAxiom> getOwlIndividualReferences() {
        return owlIndividualReferences;
    }

    public MapPointer<OWLAnonymousIndividual, OWLAxiom>
    getOwlAnonymousIndividualReferences() {
        return owlAnonymousIndividualReferences;
    }

    public MapPointer<OWLDatatype, OWLAxiom> getOwlDatatypeReferences() {
        return owlDatatypeReferences;
    }

    public MapPointer<OWLAnnotationProperty, OWLAxiom>
    getOwlAnnotationPropertyReferences() {
        return owlAnnotationPropertyReferences;
    }

    public MapPointer<OWLEntity, OWLDeclarationAxiom> getDeclarationsByEntity() {
        return declarationsByEntity;
    }

    public MapPointer<AxiomType<?>, OWLAxiom> getAxiomsByType() {
        return axiomsByType;
    }

    @SuppressWarnings("unused")
    class AddAxiomVisitor implements OWLAxiomVisitor, Serializable {
        private static final long serialVersionUID = 30402L;

        public void visit(OWLSubClassOfAxiom axiom) {
            if (!axiom.getSubClass().isAnonymous()) {
                OWLClass subClass = (OWLClass) axiom.getSubClass();
                add(getSubClassAxiomsByLHS(), subClass, axiom);
                add(getClassAxiomsByClass(), subClass, axiom);
            } else {
                addGeneralClassAxioms(axiom);
            }
            if (!axiom.getSuperClass().isAnonymous()) {
                add(getSubClassAxiomsByRHS(), (OWLClass) axiom.getSuperClass(), axiom);
            }
        }

        public void visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            add(getNegativeObjectPropertyAssertionAxiomsByIndividual(),
                    axiom.getSubject(), axiom);
        }

        public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            add(getAsymmetricPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
            add(getReflexivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLDisjointClassesAxiom axiom) {
            boolean allAnon = true;
            // Index against each named class in the axiom
            for (OWLClassExpression desc : axiom.getClassExpressions()) {
                if (!desc.isAnonymous()) {
                    OWLClass cls = (OWLClass) desc;
                    add(getDisjointClassesAxiomsByClass(), cls, axiom);
                    add(getClassAxiomsByClass(), cls, axiom);
                    allAnon = false;
                }
            }
            if (allAnon) {
                addGeneralClassAxioms(axiom);
            }
        }

        public void visit(OWLDataPropertyDomainAxiom axiom) {
            add(getDataPropertyDomainAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLObjectPropertyDomainAxiom axiom) {
            if (axiom.getProperty() instanceof OWLObjectProperty) {
                add(getObjectPropertyDomainAxiomsByProperty(), axiom.getProperty(), axiom);
            }
        }

        public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            for (OWLObjectPropertyExpression prop : axiom.getProperties()) {
                add(getEquivalentObjectPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLInverseObjectPropertiesAxiom axiom) {
            add(getInversePropertyAxiomsByProperty(), axiom.getFirstProperty(), axiom);
            add(getInversePropertyAxiomsByProperty(), axiom.getSecondProperty(), axiom);
        }

        public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            add(getNegativeDataPropertyAssertionAxiomsByIndividual(), axiom.getSubject(),
                    axiom);
        }

        public void visit(OWLDifferentIndividualsAxiom axiom) {
            for (OWLIndividual ind : axiom.getIndividuals()) {
                add(getDifferentIndividualsAxiomsByIndividual(), ind, axiom);
            }
        }

        public void visit(OWLDisjointDataPropertiesAxiom axiom) {
            for (OWLDataPropertyExpression prop : axiom.getProperties()) {
                add(getDisjointDataPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
            for (OWLObjectPropertyExpression prop : axiom.getProperties()) {
                add(getDisjointObjectPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLObjectPropertyRangeAxiom axiom) {
            add(getObjectPropertyRangeAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLObjectPropertyAssertionAxiom axiom) {
            add(getObjectPropertyAssertionsByIndividual(), axiom.getSubject(), axiom);
        }

        public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
            add(getFunctionalObjectPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
            add(getObjectSubPropertyAxiomsByLHS(), axiom.getSubProperty(), axiom);
            add(getObjectSubPropertyAxiomsByRHS(), axiom.getSuperProperty(), axiom);
        }

        public void visit(OWLDisjointUnionAxiom axiom) {
            add(getDisjointUnionAxiomsByClass(), axiom.getOWLClass(), axiom);
            add(getClassAxiomsByClass(), axiom.getOWLClass(), axiom);
        }

        public void visit(OWLDeclarationAxiom axiom) {
            add(getDeclarationsByEntity(), axiom.getEntity(), axiom);
        }

        public void visit(OWLAnnotationAssertionAxiom axiom) {
            add(getAnnotationAssertionAxiomsBySubject(), axiom.getSubject(), axiom);
        }

        public void visit(OWLAnnotationPropertyDomainAxiom axiom) {}

        public void visit(OWLAnnotationPropertyRangeAxiom axiom) {}

        public void visit(OWLSubAnnotationPropertyOfAxiom axiom) {}

        public void visit(OWLHasKeyAxiom axiom) {
            if (!axiom.getClassExpression().isAnonymous()) {
                add(getHasKeyAxiomsByClass(), axiom.getClassExpression().asOWLClass(),
                        axiom);
            }
        }

        public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
            add(getSymmetricPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLDataPropertyRangeAxiom axiom) {
            add(getDataPropertyRangeAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLFunctionalDataPropertyAxiom axiom) {
            add(getFunctionalDataPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
            for (OWLDataPropertyExpression prop : axiom.getProperties()) {
                add(getEquivalentDataPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLClassAssertionAxiom axiom) {
            add(getClassAssertionAxiomsByIndividual(), axiom.getIndividual(), axiom);
            if (!axiom.getClassExpression().isAnonymous()) {
                add(getClassAssertionAxiomsByClass(), axiom.getClassExpression(), axiom);
            }
        }

        public void visit(OWLEquivalentClassesAxiom axiom) {
            boolean allAnon = true;
            for (OWLClassExpression desc : axiom.getClassExpressions()) {
                if (!desc.isAnonymous()) {
                    add(getEquivalentClassesAxiomsByClass(), (OWLClass) desc, axiom);
                    add(getClassAxiomsByClass(), (OWLClass) desc, axiom);
                    allAnon = false;
                }
            }
            if (allAnon) {
                addGeneralClassAxioms(axiom);
            }
        }

        public void visit(OWLDataPropertyAssertionAxiom axiom) {
            add(getDataPropertyAssertionsByIndividual(), axiom.getSubject(), axiom);
        }

        public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
            add(getTransitivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            add(getIrreflexivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLSubDataPropertyOfAxiom axiom) {
            add(getDataSubPropertyAxiomsByLHS(), axiom.getSubProperty(), axiom);
            add(getDataSubPropertyAxiomsByRHS(), axiom.getSuperProperty(), axiom);
        }

        public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            add(getInverseFunctionalPropertyAxiomsByProperty(), axiom.getProperty(),
                    axiom);
        }

        public void visit(OWLSameIndividualAxiom axiom) {
            for (OWLIndividual ind : axiom.getIndividuals()) {
                add(getSameIndividualsAxiomsByIndividual(), ind, axiom);
            }
        }

        public void visit(OWLSubPropertyChainOfAxiom axiom) {
            addPropertyChainSubPropertyAxioms(axiom);
        }

        public void visit(SWRLRule rule) {}

        public void visit(OWLDatatypeDefinitionAxiom axiom) {}
    }

    @SuppressWarnings("unused")
    class RemoveAxiomVisitor implements OWLAxiomVisitor, Serializable {
        private static final long serialVersionUID = 30402L;

        public void visit(OWLSubClassOfAxiom axiom) {
            if (!axiom.getSubClass().isAnonymous()) {
                OWLClass subClass = (OWLClass) axiom.getSubClass();
                remove(getSubClassAxiomsByLHS(), subClass, axiom);
                remove(getClassAxiomsByClass(), subClass, axiom);
            } else {
                removeGeneralClassAxioms(axiom);
            }
            if (!axiom.getSuperClass().isAnonymous()) {
                remove(getSubClassAxiomsByRHS(), axiom.getSuperClass().asOWLClass(),
                        axiom);
            }
        }

        public void visit(OWLNegativeObjectPropertyAssertionAxiom axiom) {
            remove(getNegativeObjectPropertyAssertionAxiomsByIndividual(),
                    axiom.getSubject(), axiom);
        }

        public void visit(OWLAsymmetricObjectPropertyAxiom axiom) {
            remove(getAsymmetricPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLReflexiveObjectPropertyAxiom axiom) {
            remove(getReflexivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLDisjointClassesAxiom axiom) {
            boolean allAnon = true;
            for (OWLClassExpression desc : axiom.getClassExpressions()) {
                if (!desc.isAnonymous()) {
                    OWLClass cls = (OWLClass) desc;
                    remove(getDisjointClassesAxiomsByClass(), cls, axiom);
                    remove(getClassAxiomsByClass(), cls, axiom);
                    allAnon = false;
                }
            }
            if (allAnon) {
                removeGeneralClassAxioms(axiom);
            }
        }

        public void visit(OWLDataPropertyDomainAxiom axiom) {
            remove(getDataPropertyDomainAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLObjectPropertyDomainAxiom axiom) {
            if (axiom.getProperty() instanceof OWLObjectProperty) {
                remove(getObjectPropertyDomainAxiomsByProperty(), axiom.getProperty(),
                        axiom);
            }
        }

        public void visit(OWLEquivalentObjectPropertiesAxiom axiom) {
            for (OWLObjectPropertyExpression prop : axiom.getProperties()) {
                remove(getEquivalentObjectPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLInverseObjectPropertiesAxiom axiom) {
            remove(getInversePropertyAxiomsByProperty(), axiom.getFirstProperty(), axiom);
            remove(getInversePropertyAxiomsByProperty(), axiom.getSecondProperty(), axiom);
        }

        public void visit(OWLNegativeDataPropertyAssertionAxiom axiom) {
            remove(getNegativeDataPropertyAssertionAxiomsByIndividual(),
                    axiom.getSubject(), axiom);
        }

        public void visit(OWLDifferentIndividualsAxiom axiom) {
            for (OWLIndividual ind : axiom.getIndividuals()) {
                remove(getDifferentIndividualsAxiomsByIndividual(), ind, axiom);
            }
        }

        public void visit(OWLDisjointDataPropertiesAxiom axiom) {
            for (OWLDataPropertyExpression prop : axiom.getProperties()) {
                remove(getDisjointDataPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLDisjointObjectPropertiesAxiom axiom) {
            for (OWLObjectPropertyExpression prop : axiom.getProperties()) {
                remove(getDisjointObjectPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLObjectPropertyRangeAxiom axiom) {
            remove(getObjectPropertyRangeAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLObjectPropertyAssertionAxiom axiom) {
            remove(getObjectPropertyAssertionsByIndividual(), axiom.getSubject(), axiom);
        }

        public void visit(OWLFunctionalObjectPropertyAxiom axiom) {
            remove(getFunctionalObjectPropertyAxiomsByProperty(), axiom.getProperty(),
                    axiom);
        }

        public void visit(OWLSubObjectPropertyOfAxiom axiom) {
            remove(getObjectSubPropertyAxiomsByLHS(), axiom.getSubProperty(), axiom);
            remove(getObjectSubPropertyAxiomsByRHS(), axiom.getSuperProperty(), axiom);
        }

        public void visit(OWLDisjointUnionAxiom axiom) {
            remove(getDisjointUnionAxiomsByClass(), axiom.getOWLClass(), axiom);
            remove(getClassAxiomsByClass(), axiom.getOWLClass(), axiom);
        }

        public void visit(OWLDeclarationAxiom axiom) {
            remove(getDeclarationsByEntity(), axiom.getEntity(), axiom);
        }

        public void visit(OWLAnnotationAssertionAxiom axiom) {
            remove(getAnnotationAssertionAxiomsBySubject(), axiom.getSubject(), axiom);
        }

        public void visit(OWLAnnotationPropertyDomainAxiom axiom) {}

        public void visit(OWLAnnotationPropertyRangeAxiom axiom) {}

        public void visit(OWLSubAnnotationPropertyOfAxiom axiom) {}

        public void visit(OWLHasKeyAxiom axiom) {
            if (!axiom.getClassExpression().isAnonymous()) {
                remove(getHasKeyAxiomsByClass(), axiom.getClassExpression().asOWLClass(),
                        axiom);
            }
        }

        public void visit(OWLSymmetricObjectPropertyAxiom axiom) {
            remove(getSymmetricPropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLDataPropertyRangeAxiom axiom) {
            remove(getDataPropertyRangeAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLFunctionalDataPropertyAxiom axiom) {
            remove(getFunctionalDataPropertyAxiomsByProperty(), axiom.getProperty(),
                    axiom);
        }

        public void visit(OWLEquivalentDataPropertiesAxiom axiom) {
            for (OWLDataPropertyExpression prop : axiom.getProperties()) {
                remove(getEquivalentDataPropertyAxiomsByProperty(), prop, axiom);
            }
        }

        public void visit(OWLClassAssertionAxiom axiom) {
            remove(getClassAssertionAxiomsByIndividual(), axiom.getIndividual(), axiom);
            if (!axiom.getClassExpression().isAnonymous()) {
                remove(getClassAssertionAxiomsByClass(), axiom.getClassExpression(),
                        axiom);
            }
        }

        public void visit(OWLEquivalentClassesAxiom axiom) {
            boolean allAnon = true;
            for (OWLClassExpression desc : axiom.getClassExpressions()) {
                if (!desc.isAnonymous()) {
                    remove(getEquivalentClassesAxiomsByClass(), (OWLClass) desc, axiom);
                    remove(getClassAxiomsByClass(), (OWLClass) desc, axiom);
                    allAnon = false;
                }
            }
            if (allAnon) {
                removeGeneralClassAxioms(axiom);
            }
        }

        public void visit(OWLDataPropertyAssertionAxiom axiom) {
            remove(getDataPropertyAssertionsByIndividual(), axiom.getSubject(), axiom);
        }

        public void visit(OWLTransitiveObjectPropertyAxiom axiom) {
            remove(getTransitivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLIrreflexiveObjectPropertyAxiom axiom) {
            remove(getIrreflexivePropertyAxiomsByProperty(), axiom.getProperty(), axiom);
        }

        public void visit(OWLSubDataPropertyOfAxiom axiom) {
            remove(getDataSubPropertyAxiomsByLHS(), axiom.getSubProperty(), axiom);
            remove(getDataSubPropertyAxiomsByRHS(), axiom.getSuperProperty(), axiom);
        }

        public void visit(OWLInverseFunctionalObjectPropertyAxiom axiom) {
            remove(getInverseFunctionalPropertyAxiomsByProperty(), axiom.getProperty(),
                    axiom);
        }

        public void visit(OWLSameIndividualAxiom axiom) {
            for (OWLIndividual ind : axiom.getIndividuals()) {
                remove(getSameIndividualsAxiomsByIndividual(), ind, axiom);
            }
        }

        public void visit(OWLSubPropertyChainOfAxiom axiom) {
            removePropertyChainSubPropertyAxioms(axiom);
        }

        public void visit(SWRLRule rule) {}

        public void visit(OWLDatatypeDefinitionAxiom axiom) {
            // Just use general indexing (on the assumption that there won't be
            // many
            // datatype definitions). This could always be optimised at a later
            // stage.
        }
    }
}
