/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2018, Avicomp Services, AO
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package uk.ac.manchester.cs.owl.owlapi;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import static org.semanticweb.owlapi.vocab.OWL2Datatype.*;

/**
 * Entities that are commonly used in implementations.
 *
 * @author ignazio
 */
public class InternalizedEntities {

    // @formatter:off
    /**owl:Thing.*/             public static final OWLClass                OWL_THING                   = new OWLClassImpl                  (OWLRDFVocabulary.OWL_THING.getIRI());
    /**owl:Nothing.*/           public static final OWLClass                OWL_NOTHING                 = new OWLClassImpl                  (OWLRDFVocabulary.OWL_NOTHING.getIRI());
    /**Top object propery.*/    public static final OWLObjectProperty       OWL_TOP_OBJECT_PROPERTY     = new OWLObjectPropertyImpl         (OWLRDFVocabulary.OWL_TOP_OBJECT_PROPERTY.getIRI());
    /**Bottom object propery.*/ public static final OWLObjectProperty       OWL_BOTTOM_OBJECT_PROPERTY  = new OWLObjectPropertyImpl         (OWLRDFVocabulary.OWL_BOTTOM_OBJECT_PROPERTY.getIRI());
    /**Top data propery.*/      public static final OWLDataProperty         OWL_TOP_DATA_PROPERTY       = new OWLDataPropertyImpl           (OWLRDFVocabulary.OWL_TOP_DATA_PROPERTY.getIRI());
    /**Bottom data propery.*/   public static final OWLDataProperty         OWL_BOTTOM_DATA_PROPERTY    = new OWLDataPropertyImpl           (OWLRDFVocabulary.OWL_BOTTOM_DATA_PROPERTY.getIRI());
    /**Top datatype.*/          public static final OWLDatatype             RDFSLITERAL                 = new OWL2DatatypeImpl              (RDFS_LITERAL);
    protected static final OWLAnnotationProperty RDFS_LABEL                     = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    protected static final OWLAnnotationProperty RDFS_COMMENT                   = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.RDFS_COMMENT.getIRI());
    protected static final OWLAnnotationProperty RDFS_SEE_ALSO                  = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.RDFS_SEE_ALSO.getIRI());
    protected static final OWLAnnotationProperty RDFS_IS_DEFINED_BY             = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.RDFS_IS_DEFINED_BY.getIRI());
    protected static final OWLAnnotationProperty OWL_BACKWARD_COMPATIBLE_WITH   = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.OWL_BACKWARD_COMPATIBLE_WITH.getIRI());
    protected static final OWLAnnotationProperty OWL_INCOMPATIBLE_WITH          = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.OWL_INCOMPATIBLE_WITH.getIRI());
    protected static final OWLAnnotationProperty OWL_VERSION_INFO               = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.OWL_VERSION_INFO.getIRI());
    protected static final OWLAnnotationProperty OWL_DEPRECATED                 = new OWLAnnotationPropertyImpl(OWLRDFVocabulary.OWL_DEPRECATED.getIRI());
    protected static final OWLDatatype           PLAIN                          = new OWL2DatatypeImpl(RDF_PLAIN_LITERAL);
    protected static final OWLDatatype           LANGSTRING                     = new OWL2DatatypeImpl(RDF_LANG_STRING);
    protected static final OWLDatatype           XSDBOOLEAN                     = new OWL2DatatypeImpl(XSD_BOOLEAN);
    protected static final OWLDatatype           XSDDOUBLE                      = new OWL2DatatypeImpl(XSD_DOUBLE);
    protected static final OWLDatatype           XSDFLOAT                       = new OWL2DatatypeImpl(XSD_FLOAT);
    protected static final OWLDatatype           XSDINTEGER                     = new OWL2DatatypeImpl(XSD_INTEGER);
    protected static final OWLDatatype           XSDSTRING                      = new OWL2DatatypeImpl(XSD_STRING);
    protected static final OWLLiteral            TRUELITERAL                    = new OWLLiteralImplBoolean(true);
    protected static final OWLLiteral            FALSELITERAL                   = new OWLLiteralImplBoolean(false);
    // @formatter:on

    private InternalizedEntities() {}
}
