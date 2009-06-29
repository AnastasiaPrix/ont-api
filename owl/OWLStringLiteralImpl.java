package uk.ac.manchester.cs.owl;

import org.semanticweb.owlapi.model.*;
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
 * Author: Matthew Horridge<br>
 * The University Of Manchester<br>
 * Bio-Health Informatics Group<br>
 * Date: 26-Oct-2006<br><br>
 */
public class OWLStringLiteralImpl extends OWLLiteralImpl implements OWLStringLiteral {

    private String lang;


    public OWLStringLiteralImpl(OWLDataFactory dataFactory, String literal, String lang) {
        super(dataFactory, literal);
        this.lang = lang;
    }


    public String getLang() {
        return lang;
    }


    public boolean hasLang() {
        return lang != null;
    }


    public boolean hasLang(String lang) {
        if (lang == null) {
            return false;
        }
        if (this.lang == null) {
            return false;
        }
        return this.lang.equals(lang);
    }


    public boolean isTyped() {
        return false;
    }


    public OWLTypedLiteral asOWLStringLiteral() {
        throw new OWLRuntimeException("Not a typed constant!");
    }


    public OWLStringLiteral asRDFTextLiteral() {
        return this;
    }

    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            if (obj instanceof OWLStringLiteral) {
                String otherLang = ((OWLStringLiteral) obj).getLang();
                if (otherLang != null) {
                    return otherLang.equals(lang);
                } else {
                    return lang == null;
                }
            }
        }
        return false;
    }

    public void accept(OWLAnnotationValueVisitor visitor) {
        visitor.visit(this);
    }

    public <O> O accept(OWLAnnotationValueVisitorEx<O> visitor) {
        return visitor.visit(this);
    }

    public void accept(OWLDataVisitor visitor) {
        visitor.visit(this);
    }


    public void accept(OWLObjectVisitor visitor) {
        visitor.visit(this);
    }

    public <O> O accept(OWLDataVisitorEx<O> visitor) {
        return visitor.visit(this);
    }


    public <O> O accept(OWLObjectVisitorEx<O> visitor) {
        return visitor.visit(this);
    }

    protected int compareObjectOfSameType(OWLObject object) {
        OWLStringLiteral other = (OWLStringLiteral) object;
        int diff = getLiteral().compareTo(other.getLiteral());
        if(diff != 0) {
            return diff;
        }
        // One with out lang comes before one with lang
        if(this.getLang() == null) {
            if(other.getLang() == null) {
                return 0;
            }
            else {
                return -1;
            }
        }
        else {
            if(other.getLang() == null) {
                return 1;
            }
            else {
                return getLang().compareTo(other.getLang());
            }
        }
    }
}
