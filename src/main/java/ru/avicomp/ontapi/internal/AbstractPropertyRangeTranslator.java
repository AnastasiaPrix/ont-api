/*
 * This file is part of the ONT API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright (c) 2017, Avicomp Services, AO
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

import java.util.stream.Stream;

import org.apache.jena.vocabulary.RDFS;
import org.semanticweb.owlapi.model.HasProperty;
import org.semanticweb.owlapi.model.HasRange;
import org.semanticweb.owlapi.model.OWLAxiom;

import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntPE;
import ru.avicomp.ontapi.jena.model.OntStatement;

/**
 * base class for {@link DataPropertyRangeTranslator} and {@link ObjectPropertyRangeTranslator} and {@link AnnotationPropertyRangeTranslator}
 * example:
 * <pre>{@code foaf:name rdfs:range rdfs:Literal}</pre>
 * <p>
 * Created by @szuev on 30.09.2016.
 */
public abstract class AbstractPropertyRangeTranslator<Axiom extends OWLAxiom & HasProperty & HasRange, P extends OntPE> extends AxiomTranslator<Axiom> {
    @Override
    public void write(Axiom axiom, OntGraphModel graph) {
        WriteHelper.writeTriple(graph, axiom.getProperty(), RDFS.range, axiom.getRange(), axiom.annotations());
    }

    abstract Class<P> getView();

    @Override
    public Stream<OntStatement> statements(OntGraphModel model) {
        return model.statements(null, RDFS.range, null)
                .filter(OntStatement::isLocal)
                .filter(s -> s.getSubject().canAs(getView()));
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return statement.getPredicate().equals(RDFS.range) && statement.getSubject().canAs(getView());
    }

}
