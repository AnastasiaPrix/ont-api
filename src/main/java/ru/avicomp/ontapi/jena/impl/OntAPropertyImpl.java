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

package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.impl.PropertyImpl;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntStatement;
import ru.avicomp.ontapi.jena.vocabulary.OWL;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Implementation of Annotation Property abstraction (an URI-{@link Resource} with {@link OWL#AnnotationProperty owl:AnnotationProperty} type).
 * <p>
 * Created by szuev on 03.11.2016.
 */
public class OntAPropertyImpl extends OntPEImpl implements OntNAP {

    public OntAPropertyImpl(Node n, EnhGraph g) {
        super(n, g);
    }

    @Override
    public Class<OntNAP> getActualClass() {
        return OntNAP.class;
    }

    @Override
    public OntStatement addDomain(Resource domain) {
        return addStatement(RDFS.domain, checkNamed(domain));
    }

    @Override
    public OntStatement addRange(Resource range) {
        return addStatement(RDFS.range, checkNamed(range));
    }

    @Override
    public Stream<Resource> domain() {
        return objects(RDFS.domain, Resource.class).filter(RDFNode::isURIResource);
    }

    @Override
    public Stream<Resource> range() {
        return objects(RDFS.range, Resource.class).filter(RDFNode::isURIResource);
    }

    @Override
    public boolean isBuiltIn() {
        return getModel().isBuiltIn(this);
    }

    @Override
    public Property inModel(Model m) {
        return getModel() == m ? this : m.createProperty(getURI());
    }

    @Override
    public Optional<OntStatement> findRootStatement() {
        return getOptionalRootStatement(this, OWL.AnnotationProperty);
    }

    @Override
    public int getOrdinal() {
        return new PropertyImpl(node, enhGraph).getOrdinal();
    }
}
