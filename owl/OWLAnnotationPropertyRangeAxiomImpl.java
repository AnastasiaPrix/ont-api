package uk.ac.manchester.cs.owl;

import org.semanticweb.owl.model.*;/*
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
public class OWLAnnotationPropertyRangeAxiomImpl extends OWLAxiomImpl implements OWLAnnotationPropertyRange {

    private OWLAnnotationProperty property;

    protected IRI range;

    public OWLAnnotationPropertyRangeAxiomImpl(OWLDataFactory dataFactory, OWLAnnotationProperty property, IRI range) {
        super(dataFactory);
        this.property = property;
        this.range = range;
    }

    public OWLAnnotationProperty getProperty() {
        return property;
    }

    public IRI getRange() {
        return range;
    }

    public AxiomType getAxiomType() {
        return AxiomType.ANNOTATION_PROPERTY_RANGE;
    }

    public boolean isLogicalAxiom() {
        return false;
    }

    protected int compareObjectOfSameType(OWLObject object) {
        OWLAnnotationPropertyRange other = (OWLAnnotationPropertyRange) object;
        int diff = property.compareTo(other.getProperty());
        if (diff != 0) {
            return diff;
        }
        return range.compareTo(other.getRange());
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
