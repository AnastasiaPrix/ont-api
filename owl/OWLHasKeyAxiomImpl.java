package uk.ac.manchester.cs.owl;

import org.semanticweb.owl.model.*;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;/*
 * Copyright (C) 2008, University of Manchester
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
 * Author: Matthew Horridge<br> The University of Manchester<br> Information Management Group<br>
 * Date: 17-Jan-2009
 */
public class OWLHasKeyAxiomImpl extends OWLAxiomImpl implements OWLHasKeyAxiom {

    private OWLClassExpression expression;

    private Set<OWLObjectPropertyExpression> objectPropertyExpressions;

    private Set<OWLDataPropertyExpression> dataPropertyExpressions;

    public OWLHasKeyAxiomImpl(OWLDataFactory dataFactory, OWLClassExpression expression, Set<? extends OWLObjectPropertyExpression> objectPropertyExpressions, Set<? extends OWLDataPropertyExpression> dataPropertyExpressions, OWLAnnotation... annotations) {
        super(dataFactory, annotations);
        this.expression = expression;
        this.objectPropertyExpressions = Collections.unmodifiableSet(new TreeSet<OWLObjectPropertyExpression>(objectPropertyExpressions));
        this.dataPropertyExpressions = Collections.unmodifiableSet(new TreeSet<OWLDataPropertyExpression>(dataPropertyExpressions));
    }

    public AxiomType getAxiomType() {
        return AxiomType.HAS_KEY;
    }

    public boolean isLogicalAxiom() {
        return true;
    }

    public OWLClassExpression getClassExpression() {
        return expression;
    }

    public Set<OWLDataPropertyExpression> getDataPropertyExpressions() {
        return dataPropertyExpressions;
    }

    public Set<OWLObjectPropertyExpression> getObjectPropertyExpressions() {
        return objectPropertyExpressions;
    }

    protected int compareObjectOfSameType(OWLObject object) {
        OWLHasKeyAxiom other = (OWLHasKeyAxiom) object;
        int diff = expression.compareTo(other.getClassExpression());
        if (diff != 0) {
            return diff;
        }
        diff = compareSets(objectPropertyExpressions, other.getObjectPropertyExpressions());
        if (diff != 0) {
            return diff;
        }
        return compareSets(dataPropertyExpressions, other.getDataPropertyExpressions());
    }

    public void accept(OWLObjectVisitor visitor) {
        visitor.visit(this);
    }

    public <O> O accept(OWLObjectVisitorEx<O> visitor) {
        return visitor.visit(this);
    }

    public void accept(OWLAxiomVisitor visitor) {
        visitor.visit(this);
    }

    public <O> O accept(OWLAxiomVisitorEx<O> visitor) {
        return visitor.visit(this);
    }
}
