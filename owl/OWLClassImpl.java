package uk.ac.manchester.cs.owl;

import org.semanticweb.owl.model.*;
import org.semanticweb.owl.vocab.OWLRDFVocabulary;

import java.net.URI;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
/*
 * Copyright (C) 2006, University of Manchester
 *
 * Modifications to the initial code base are copyright of their
 * respective authors, or their employers as appropriate.  Authorship
 * of the modifications may be determined from the ChangeLog placed at
 * the end of this file.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.

 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.

 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */


/**
 * Author: Matthew Horridge<br> The University Of Manchester<br> Bio-Health Informatics Group<br> Date:
 * 25-Oct-2006<br><br>
 */
public class OWLClassImpl extends OWLObjectImpl implements OWLClass {

    private IRI iri;

    private boolean isThing;

    private boolean isNothing;


    public OWLClassImpl(OWLDataFactory dataFactory, IRI iri) {
        super(dataFactory);
        this.iri = iri;
        isThing = getURI().equals(OWLRDFVocabulary.OWL_THING.getURI());
        isNothing = getURI().equals(OWLRDFVocabulary.OWL_NOTHING.getURI());
    }

    public IRI getIRI() {
        return iri;
    }

    public boolean isBuiltIn() {
        return isOWLThing() || isOWLNothing();
    }


    public boolean isAnonymous() {
        return false;
    }


    public boolean isClassExpressionLiteral() {
        return true;
    }


    public OWLClass asOWLClass() {
        return this;
    }


    public URI getURI() {
        return iri.toURI();
    }


    public boolean isOWLThing() {
        return isThing;
    }


    public boolean isOWLNothing() {
        return isNothing;
    }


    public OWLClassExpression getNNF() {
        return this;
    }


    public Set<OWLClassExpression> asConjunctSet() {
        return Collections.singleton((OWLClassExpression) this);
    }


    public Set<OWLClassExpression> asDisjunctSet() {
        return Collections.singleton((OWLClassExpression) this);
    }


    public OWLClassExpression getComplementNNF() {
        return getOWLDataFactory().getObjectComplementOf(this);
    }


    public Set<OWLSubClassOfAxiom> getSubClassAxioms(OWLOntology ontology) {
        return ontology.getSubClassAxiomsForSubClass(this);
    }


    public Set<OWLEquivalentClassesAxiom> getEquivalentClassesAxioms(OWLOntology ontology) {
        return ontology.getEquivalentClassesAxioms(this);
    }


    public Set<OWLDisjointClassesAxiom> getDisjointClassesAxioms(OWLOntology ontology) {
        return ontology.getDisjointClassesAxioms(this);
    }


    public Set<OWLDisjointUnionAxiom> getDisjointUnionAxioms(OWLOntology ontology) {
        return ontology.getDisjointUnionAxioms(this);
    }


    public Set<OWLClassExpression> getSuperClasses(OWLOntology ontology) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLSubClassOfAxiom axiom : getSubClassAxioms(ontology)) {
            result.add(axiom.getSuperClass());
        }
        return result;
    }


    public Set<OWLClassExpression> getSuperClasses(Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLOntology ont : ontologies) {
            result.addAll(getSuperClasses(ont));
        }
        return result;
    }


    public Set<OWLClassExpression> getSubClasses(OWLOntology ontology) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLSubClassOfAxiom axiom : ontology.getSubClassAxiomsForSuperClass(this)) {
            result.add(axiom.getSubClass());
        }
        return result;
    }


    public Set<OWLClassExpression> getSubClasses(Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLOntology ont : ontologies) {
            result.addAll(getSubClasses(ont));
        }
        return result;
    }


    public Set<OWLClassExpression> getEquivalentClasses(OWLOntology ontology) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLEquivalentClassesAxiom axiom : getEquivalentClassesAxioms(ontology)) {
            result.addAll(axiom.getDescriptions());
        }
        // Don't have the class equivalent to itself
        result.remove(this);
        return result;
    }


    public Set<OWLClassExpression> getEquivalentClasses(Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLOntology ont : ontologies) {
            result.addAll(getEquivalentClasses(ont));
        }
        return result;
    }


    public Set<OWLClassExpression> getDisjointClasses(OWLOntology ontology) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLDisjointClassesAxiom axiom : getDisjointClassesAxioms(ontology)) {
            result.addAll(axiom.getDescriptions());
        }
        // The disjoint classes will contain this class - remove it!
        result.remove(this);
        return result;
    }


    public Set<OWLClassExpression> getDisjointClasses(Set<OWLOntology> ontologies) {
        Set<OWLClassExpression> result = new TreeSet<OWLClassExpression>();
        for (OWLOntology ont : ontologies) {
            result.addAll(getDisjointClasses(ont));
        }
        return result;
    }


    public Set<OWLIndividual> getIndividuals(OWLOntology ontology) {
        Set<OWLIndividual> result = new TreeSet<OWLIndividual>();
        for (OWLClassAssertionAxiom ax : ontology.getClassAssertionAxioms(this)) {
            result.add(ax.getIndividual());
        }
        return result;
    }


    public Set<OWLIndividual> getIndividuals(Set<OWLOntology> ontologies) {
        Set<OWLIndividual> result = new TreeSet<OWLIndividual>();
        for (OWLOntology ont : ontologies) {
            result.addAll(getIndividuals(ont));
        }
        return result;
    }


    public Set<OWLAnnotation> getAnnotations(OWLOntology ontology) {
        return ImplUtils.getAnnotations(this, Collections.singleton(ontology));
    }


    public Set<OWLAnnotationAssertionAxiom> getAnnotationAssertionAxioms(OWLOntology ontology) {
        return ImplUtils.getAnnotationAxioms(this, Collections.singleton(ontology));
    }


    public Set<OWLAnnotation> getAnnotations(OWLOntology ontology, URI annotationURI) {
        return ImplUtils.getAnnotations(this, annotationURI, Collections.singleton(ontology));
    }


    /**
     * Determines if this class has at least one equivalent class in the specified ontology.
     *
     * @param ontology The ontology to examine for axioms.
     */
    public boolean isDefined(OWLOntology ontology) {
        return !ontology.getEquivalentClassesAxioms(this).isEmpty();
    }


    public boolean isDefined(Set<OWLOntology> ontologies) {
        for (OWLOntology ont : ontologies) {
            if (isDefined(ont)) {
                return true;
            }
        }
        return false;
    }


    public OWLDataProperty asOWLDataProperty() {
        throw new OWLRuntimeException("Not a data property!");
    }


    public OWLDatatype asOWLDataType() {
        throw new OWLRuntimeException("Not a data type!");
    }


    public OWLNamedIndividual asOWLIndividual() {
        throw new OWLRuntimeException("Not an individual!");
    }


    public OWLObjectProperty asOWLObjectProperty() {
        throw new OWLRuntimeException("Not an object property");
    }


    public boolean isOWLClass() {
        return true;
    }


    public boolean isOWLDataProperty() {
        return false;
    }


    public boolean isOWLDataType() {
        return false;
    }


    public boolean isOWLIndividual() {
        return false;
    }


    public boolean isOWLObjectProperty() {
        return false;
    }


    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            if (!(obj instanceof OWLClass)) {
                return false;
            }
            URI otherURI = ((OWLClass) obj).getURI();
            String otherFragment = otherURI.getFragment();
            String thisFragment = getURI().getFragment();
            if (otherFragment != null && thisFragment != null && !otherFragment.equals(thisFragment)) {
                return false;
            } else {
                return otherURI.equals(getURI());
            }

        }
        return false;
    }


    public void accept(OWLClassExpressionVisitor visitor) {
        visitor.visit(this);
    }


    public void accept(OWLEntityVisitor visitor) {
        visitor.visit(this);
    }


    public void accept(OWLObjectVisitor visitor) {
        visitor.visit(this);
    }


    public void accept(OWLNamedObjectVisitor visitor) {
        visitor.visit(this);
    }


    public <O> O accept(OWLEntityVisitorEx<O> visitor) {
        return visitor.visit(this);
    }


    public <O> O accept(OWLClassExpressionVisitorEx<O> visitor) {
        return visitor.visit(this);
    }


    public <O> O accept(OWLObjectVisitorEx<O> visitor) {
        return visitor.visit(this);
    }


    protected int compareObjectOfSameType(OWLObject object) {
        OWLClass other = (OWLClass) object;
        return getURI().compareTo(other.getURI());
    }
}
