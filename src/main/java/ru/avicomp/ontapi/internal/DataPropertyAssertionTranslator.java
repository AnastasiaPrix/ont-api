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

package ru.avicomp.ontapi.internal;

import org.apache.jena.util.iterator.ExtendedIterator;
import org.semanticweb.owlapi.model.*;
import ru.avicomp.ontapi.jena.model.OntGraphModel;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNDP;
import ru.avicomp.ontapi.jena.model.OntStatement;

import java.util.Collection;

/**
 * property that belongs to individual.
 * individual could be anonymous!
 * <p>
 * Created by @szuev on 28.09.2016.
 */
public class DataPropertyAssertionTranslator extends AxiomTranslator<OWLDataPropertyAssertionAxiom> {
    @Override
    public void write(OWLDataPropertyAssertionAxiom axiom, OntGraphModel model) {
        WriteHelper.writeAssertionTriple(model, axiom.getSubject(), axiom.getProperty(), axiom.getObject(), axiom.annotations());
    }

    /**
     * Lists positive data property assertions: the rule {@code a R v}.
     * See <a href='https://www.w3.org/TR/owl2-quick-reference/'>Assertions</a>
     *
     * @param model {@link OntGraphModel} the model
     * @return {@link ExtendedIterator} of {@link OntStatement}s
     */
    @Override
    protected ExtendedIterator<OntStatement> listStatements(OntGraphModel model) {
        return listStatements(model, null, null, null)
                .filterKeep(s -> s.isData()
                        && s.getObject().isLiteral()
                        && s.getSubject().canAs(OntIndividual.class));
    }

    @Override
    public boolean testStatement(OntStatement statement) {
        return statement.isData()
                && statement.getObject().isLiteral()
                && statement.getSubject().canAs(OntIndividual.class);
    }

    @Override
    public ONTObject<OWLDataPropertyAssertionAxiom> toAxiom(OntStatement statement) {
        InternalDataFactory reader = getDataFactory(statement.getModel());
        ONTObject<? extends OWLIndividual> i = reader.get(statement.getSubject(OntIndividual.class));
        ONTObject<OWLDataProperty> p = reader.get(statement.getPredicate().as(OntNDP.class));
        ONTObject<OWLLiteral> l = reader.get(statement.getObject().asLiteral());
        Collection<ONTObject<OWLAnnotation>> annotations = reader.get(statement);
        OWLDataPropertyAssertionAxiom res = reader.getOWLDataFactory()
                .getOWLDataPropertyAssertionAxiom(p.getObject(), i.getObject(), l.getObject(),
                        ONTObject.extract(annotations));
        return ONTObject.create(res, statement).append(annotations).append(i).append(p).append(l);
    }
}
