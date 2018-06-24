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

package ru.avicomp.ontapi.transforms;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;
import ru.avicomp.ontapi.jena.utils.Iter;
import ru.avicomp.ontapi.jena.utils.Models;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;
import ru.avicomp.ontapi.jena.vocabulary.SWRL;
import ru.avicomp.ontapi.jena.vocabulary.XSD;
import ru.avicomp.ontapi.transforms.vocabulary.AVC;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class to perform the final tuning of the OWL-2 ontology: mostly for fixing missed owl-declarations where it is possible.
 * It have to be running after {@link RDFSTransform} and {@link OWLCommonTransform}.
 * <p>
 * This transformer is designed to put in order any external (mainly none-OWL2) ontologies.
 * Also there are lots examples of incomplete or wrong ontologies provided by the tests from OWL-API contract pack,
 * which are not necessarily RDFS or OWL1.
 * And it seems such situations have to be relative rare in the real world, since
 * any API which meets specification would not produce ontologies, when there is some true parts of OWL2,
 * but no explicit declarations or some other components from which they consist.
 * At least one can be sure that ONT-API does not provide anything that only partially complies with the specification;
 * but for correct output the input should also be correct.
 * <p>
 * Consists of two inner transforms:
 * <ul>
 * <li>The first, {@link ManifestDeclarator}, works with the obvious cases
 * when the type of the left or the right statements part is defined by the predicate or from some other clear hints.
 * E.g. if we have triple "A rdfs:subClassOf B" then we know exactly - both "A" and "B" are owl-class expressions.
 * </li>
 * <li>The second, {@link ReasonerDeclarator}, performs iterative analyzing of whole graph to choose the correct entities type.
 * E.g. we can have owl-restriction (existential/universal quantification)
 * "_:x rdf:type owl:Restriction; owl:onProperty A; owl:allValuesFrom B",
 * where "A" and "B" could be either object property and class expressions or data property and data-range,
 * and therefore we need to find other entries of these two entities in the graph;
 * for this example the only one declaration either of "A" or "B" is enough.
 * </li>
 * </ul>
 *
 * @see <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL2 Short Guide</a>
 */
@SuppressWarnings("WeakerAccess")
public class OWLDeclarationTransform extends Transform {

    public OWLDeclarationTransform(Graph graph) {
        super(graph);
    }

    @Override
    public void perform() {
        try {
            new ManifestDeclarator(graph).perform();
            new ReasonerDeclarator(graph).perform();
        } finally {
            finalActions();
        }
    }

    protected void finalActions() {
        getBaseModel().removeAll(null, RDF.type, AVC.AnonymousIndividual);
        // at times the ontology could contain some rdfs garbage, even if other transformers (OWLTransformer, RDFSTransformer) have been used.
        Set<Resource> properties = statements(null, RDF.type, RDF.Property)
                .map(Statement::getSubject)
                .filter(s -> statements(s, RDF.type, null).count() > 1)
                .collect(Collectors.toSet());
        Set<Resource> classes = statements(null, RDF.type, RDFS.Class)
                .map(Statement::getSubject)
                .filter(s -> statements(s, RDF.type, null).count() > 1)
                .collect(Collectors.toSet());
        properties.forEach(p -> undeclare(p, RDF.Property));
        classes.forEach(c -> undeclare(c, RDFS.Class));
    }

    /**
     * The transformer to restore declarations in the clear cases
     * (all notations are taken from the <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL2 Short Guide</a>):
     * 1) The declaration in annotation
     * <ul>
     * <li>{@code _:x a owl:Annotation; owl:annotatedSource s; owl:annotatedProperty rdf:type; owl:annotatedTarget U.}</li>
     * </ul>
     * 2) Explicit class:
     * <ul>
     * <li>{@code C1 rdfs:subClassOf C2}</li>
     * <li>{@code C1 owl:disjointWith C2}</li>
     * <li>{@code _:x owl:complementOf C}</li>
     * <li>{@code _:x rdf:type owl:AllDisjointClasses; owl:members ( C1 ... Cn )}</li>
     * <li>{@code CN owl:disjointUnionOf ( C1 ... Cn )}</li>
     * <li>{@code C owl:hasKey ( P1 ... Pm R1 ... Rn )}</li>
     * <li>{@code _:x a owl:Restriction; _:x owl:onClass C}</li>
     * </ul>
     * 3) Explicit data-range:
     * <ul>
     * <li>{@code _:x owl:datatypeComplementOf D.}</li>
     * <li>{@code _:x owl:onDatatype DN; owl:withRestrictions ( _:x1 ... _:xn )}</li>
     * <li>{@code _x: a owl:Restriction; owl:onProperties ( R1 ... Rn ); owl:allValuesFrom Dn}</li>
     * <li>{@code _x: a owl:Restriction; owl:onProperties ( R1 ... Rn ); owl:someValuesFrom Dn}</li>
     * <li>{@code _:x a owl:Restriction; owl:onDataRange D.}</li>
     * </ul>
     * 4) Data range or class expression:
     * <ul><li>{@code _:x owl:oneOf ( a1 ... an )} or {@code _:x owl:oneOf ( v1 ... vn )}</li></ul>
     * 5) Explicit object property expression:
     * <ul>
     * <li>{@code P a owl:InverseFunctionalProperty}</li>
     * <li>{@code P rdf:type owl:ReflexiveProperty}</li>
     * <li>{@code P rdf:type owl:IrreflexiveProperty}</li>
     * <li>{@code P rdf:type owl:SymmetricProperty}</li>
     * <li>{@code P rdf:type owl:AsymmetricProperty}</li>
     * <li>{@code P rdf:type owl:TransitiveProperty}</li>
     * <li>{@code P1 owl:inverseOf P2}</li>
     * <li>{@code P owl:propertyChainAxiom ( P1 ... Pn )}</li>
     * <li>{@code _:x a owl:Restriction; owl:onProperty P; owl:hasSelf "true"^^xsd:boolean}</li>
     * </ul>
     * 6) Data property or object property expression:
     * <ul>
     * <li>{@code _:x a owl:Restriction; owl:onProperty R; owl:hasValue v} or {@code _:x a owl:Restriction; owl:onProperty P; owl:hasValue a}</li>
     * <li>{@code _:x rdf:type owl:NegativePropertyAssertion; owl:sourceIndividual a1; owl:assertionProperty P; owl:targetIndividual a2} or
     * {@code _:x rdf:type owl:NegativePropertyAssertion; owl:sourceIndividual a1; owl:assertionProperty R; owl:targetValue v}</li>
     * </ul>
     * 7) Explicit individuals:
     * <ul>
     * <li>{@code a1 owl:sameAs a2} and {@code a1 owl:differentFrom a2}</li>
     * <li>{@code _:x rdf:type owl:AllDifferent; owl:members ( a1 ... an )}</li>
     * </ul>
     * 8) Class assertions (individuals declarations):
     * <ul>
     * <li>{@code a rdf:type C}</li>
     * </ul>
     * 9) SWRL rules. see {@link SWRL}
     */
    @SuppressWarnings("WeakerAccess")
    public static class ManifestDeclarator extends BaseDeclarator {
        protected Set<Resource> forbiddenClassCandidates;

        public ManifestDeclarator(Graph graph) {
            super(graph);
        }

        @Override
        public void perform() {
            parseAnnotations();
            parseClassExpressions();
            parseDataRangeExpressions();
            parseOneOfExpression();
            parseObjectPropertyExpressions();
            parseObjectOrDataProperties();
            parseIndividuals();
            parseClassAssertions();
            parseSWRL();
        }

        public void parseAnnotations() {
            // "_:x a owl:Annotation; owl:annotatedSource entity;
            // owl:annotatedProperty rdf:type; owl:annotatedTarget type." => "entity rdf:type type"
            statements(null, OWL.annotatedProperty, RDF.type)
                    .map(Statement::getSubject).filter(RDFNode::isAnon)
                    .filter(s -> s.hasProperty(RDF.type, OWL.Annotation) || s.hasProperty(RDF.type, OWL.Axiom))
                    .forEach(r -> {
                        Resource source = getObjectResource(r, OWL.annotatedSource);
                        Resource target = getObjectResource(r, OWL.annotatedTarget);
                        if (source == null || target == null) return;
                        declare(source, target);
                    });

        }

        public void parseClassExpressions() {
            // "C1 rdfs:subClassOf C2" or "C1 owl:disjointWith C2"
            Stream.of(RDFS.subClassOf, OWL.disjointWith)
                    .map(p -> statements(null, p, null))
                    .flatMap(Function.identity())
                    .map(s -> Stream.of(s.getSubject(), s.getObject()))
                    .flatMap(Function.identity())
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource).distinct().forEach(this::declareClass);
            // "_:x owl:complementOf C"
            statements(null, OWL.complementOf, null)
                    .map(s -> Stream.of(s.getSubject(), s.getObject()))
                    .flatMap(Function.identity())
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource).distinct().forEach(this::declareClass);
            // "_:x rdf:type owl:AllDisjointClasses ; owl:members ( C1 ... Cn )"
            statements(null, RDF.type, OWL.AllDisjointClasses)
                    .filter(s -> s.getSubject().isAnon())
                    .map(s -> members(s.getSubject(), OWL.members))
                    .flatMap(Function.identity()).distinct().forEach(this::declareClass);
            // "CN owl:disjointUnionOf ( C1 ... Cn )"
            statements(null, OWL.disjointUnionOf, null).map(Statement::getSubject).filter(RDFNode::isURIResource)
                    .forEach(c -> {
                        declareClass(c);
                        members(c, OWL.disjointUnionOf).forEach(this::declareClass);
                    });
            // "C owl:hasKey ( P1 ... Pm R1 ... Rn )"
            statements(null, OWL.hasKey, null).map(Statement::getSubject).forEach(this::declareClass);
            // "_:x a owl:Restriction; _:x owl:onClass C"
            statements(null, OWL.onClass, null)
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getSubject().hasProperty(OWL.onProperty))
                    .filter(s -> s.getObject().isResource()).forEach(s -> {
                declare(s.getSubject(), OWL.Restriction);
                declareClass(s.getObject().asResource());
            });
        }

        public void parseDataRangeExpressions() {
            // "_:x owl:datatypeComplementOf D."
            statements(null, OWL.datatypeComplementOf, null).filter(s -> s.getObject().isResource())
                    .forEach(s -> {
                        declareDatatype(s.getSubject());
                        declareDatatype(s.getObject().asResource());
                    });
            // "_:x owl:onDatatype DN; owl:withRestrictions ( _:x1 ... _:xn )"
            statements(null, OWL.onDatatype, null)
                    .filter(s -> s.getObject().isURIResource())
                    .filter(s -> s.getSubject().hasProperty(OWL.withRestrictions))
                    .filter(s -> s.getSubject().getProperty(OWL.withRestrictions).getObject().canAs(RDFList.class))
                    .forEach(s -> {
                        declareDatatype(s.getSubject());
                        declareDatatype(s.getObject().asResource());
                    });

            // "_x: a owl:Restriction;  owl:onProperties ( R1 ... Rn ); owl:allValuesFrom Dn" or
            // "_x: a owl:Restriction;  owl:onProperties ( R1 ... Rn ); owl:someValuesFrom Dn"
            statements(null, OWL.onProperties, null)
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getObject().canAs(RDFList.class))
                    .map(Statement::getSubject).forEach(r -> {
                Stream.of(OWL.allValuesFrom, OWL.someValuesFrom).map(p -> statements(r, p, null))
                        .flatMap(Function.identity())
                        .map(Statement::getObject)
                        .filter(RDFNode::isAnon).forEach(n -> declareDatatype(n.asResource()));
                declare(r, OWL.Restriction);
            });
            // "_:x a owl:Restriction; owl:onDataRange D."
            statements(null, OWL.onDataRange, null)
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getSubject().hasProperty(OWL.onProperty))
                    .filter(s -> s.getObject().isResource()).forEach(s -> {
                declare(s.getSubject(), OWL.Restriction);
                declareDatatype(s.getObject().asResource());
            });
        }

        public void parseOneOfExpression() {
            // "_:x owl:oneOf ( a1 ... an )" or "_:x owl:oneOf ( v1 ... vn )"
            statements(null, OWL.oneOf, null)
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getObject().canAs(RDFList.class)).forEach(s -> {
                List<RDFNode> values = s.getObject().as(RDFList.class).asJavaList();
                if (values.isEmpty()) return;
                if (values.stream().allMatch(RDFNode::isLiteral)) {
                    declareDatatype(s.getSubject());
                } else {
                    declareClass(s.getSubject());
                    values.forEach(v -> declareIndividual(v.asResource()));
                }
            });
        }

        public void parseObjectPropertyExpressions() {
            // "_:x a owl:InverseFunctionalProperty", etc
            Stream.of(OWL.InverseFunctionalProperty,
                    OWL.ReflexiveProperty,
                    OWL.IrreflexiveProperty,
                    OWL.SymmetricProperty,
                    OWL.AsymmetricProperty,
                    OWL.TransitiveProperty).map(p -> statements(null, RDF.type, p))
                    .flatMap(Function.identity())
                    .map(Statement::getSubject).distinct().forEach(this::declareObjectProperty);
            // "P1 owl:inverseOf P2"
            statements(null, OWL.inverseOf, null)
                    .filter(s -> s.getObject().isURIResource())
                    .map(s -> Stream.of(s.getSubject(), s.getObject().asResource()))
                    .flatMap(Function.identity()).distinct().forEach(this::declareObjectProperty);
            // 	"P owl:propertyChainAxiom (P1 ... Pn)"
            statements(null, OWL.propertyChainAxiom, null)
                    .map(this::subjectAndObjectsAsSet)
                    .map(Collection::stream)
                    .flatMap(Function.identity())
                    .distinct()
                    .forEach(this::declareObjectProperty);
            // "_:x a owl:Restriction; owl:onProperty P; owl:hasSelf "true"^^xsd:boolean"
            statements(null, OWL.hasSelf, null)
                    .filter(s -> Objects.equals(Models.TRUE, s.getObject()))
                    .map(Statement::getSubject)
                    .filter(RDFNode::isAnon)
                    .filter(s -> s.hasProperty(OWL.onProperty))
                    .forEach(s -> {
                        Resource p = getObjectResource(s, OWL.onProperty);
                        if (p == null) return;
                        declare(s, OWL.Restriction);
                        declareObjectProperty(p);
                    });
        }

        public void parseObjectOrDataProperties() {
            // "_:x a owl:Restriction; owl:onProperty R; owl:hasValue v" or "_:x a owl:Restriction; owl:onProperty P; owl:hasValue a"
            statements(null, OWL.hasValue, null).forEach(s -> {
                Resource p = getObjectResource(s.getSubject(), OWL.onProperty);
                if (p == null) return;
                declare(s.getSubject(), OWL.Restriction);
                if (s.getObject().isLiteral()) {
                    declareDataProperty(p);
                } else {
                    declareObjectProperty(p);
                    declareIndividual(s.getObject().asResource());
                }
            });
            // "_:x rdf:type owl:NegativePropertyAssertion" with owl:targetIndividual
            statements(null, RDF.type, OWL.NegativePropertyAssertion)
                    .map(Statement::getSubject)
                    .filter(RDFNode::isAnon)
                    .forEach(r -> {
                        Resource source = getObjectResource(r, OWL.sourceIndividual);
                        Resource prop = getObjectResource(r, OWL.assertionProperty);
                        if (source == null || prop == null) return;
                        Resource i = getObjectResource(r, OWL.targetIndividual);
                        if (i == null && getObjectLiteral(r, OWL.targetValue) == null) return;
                        declareIndividual(source);
                        if (i != null) {
                            declareIndividual(i);
                            declareObjectProperty(prop);
                        } else {
                            declareDataProperty(prop);
                        }
                    });
        }

        public void parseIndividuals() {
            // "a1 owl:sameAs a2" and "a1 owl:differentFrom a2"
            Stream.of(OWL.sameAs, OWL.differentFrom)
                    .map(p -> statements(null, p, null))
                    .flatMap(Function.identity())
                    .map(s -> Stream.of(s.getSubject(), s.getObject()))
                    .flatMap(Function.identity())
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource)
                    .distinct().forEach(this::declareIndividual);
            // "_:x rdf:type owl:AllDifferent; owl:members (a1 ... an)"
            statements(null, RDF.type, OWL.AllDifferent)
                    .filter(s -> s.getSubject().isAnon())
                    .map(Statement::getSubject)
                    .map(s -> Stream.concat(members(s, OWL.members), members(s, OWL.distinctMembers)))
                    .flatMap(Function.identity()).distinct().forEach(this::declareIndividual);
        }

        protected Set<Resource> forbiddenClassCandidates() {
            if (forbiddenClassCandidates != null) return forbiddenClassCandidates;
            forbiddenClassCandidates = new HashSet<>(builtIn.reservedResources());
            forbiddenClassCandidates.add(AVC.AnonymousIndividual);
            forbiddenClassCandidates.removeAll(builtIn.classes());
            return forbiddenClassCandidates;
        }

        public void parseClassAssertions() {
            // "a rdf:type C"
            Set<Statement> statements = statements(null, RDF.type, null)
                    .filter(s -> s.getObject().isResource())
                    .filter(s -> !forbiddenClassCandidates().contains(s.getObject().asResource()))
                    .collect(Collectors.toSet());
            statements.forEach(s -> {
                declareIndividual(s.getSubject());
                declareClass(s.getObject().asResource());
            });
        }

        public void parseSWRL() {
            // first IArg
            processSWRL(SWRL.argument1,
                    s -> s.getSubject().isAnon() &&
                            Stream.of(SWRL.ClassAtom, SWRL.DatavaluedPropertyAtom, SWRL.IndividualPropertyAtom,
                                    SWRL.DifferentIndividualsAtom, SWRL.SameIndividualAtom).anyMatch(t -> hasType(s.getSubject(), t)),
                    r -> !hasType(r, SWRL.Variable),
                    this::declareIndividual);
            // second IArg
            processSWRL(SWRL.argument2,
                    s -> s.getSubject().isAnon() &&
                            Stream.of(SWRL.IndividualPropertyAtom,
                                    SWRL.DifferentIndividualsAtom, SWRL.SameIndividualAtom).anyMatch(t -> hasType(s.getSubject(), t)),
                    r -> !hasType(r, SWRL.Variable),
                    this::declareIndividual);
            // class
            processSWRL(SWRL.classPredicate,
                    s -> s.getSubject().isAnon() && hasType(s.getSubject(), SWRL.ClassAtom),
                    null, this::declareClass);
            // data-range
            processSWRL(SWRL.dataRange,
                    s -> s.getSubject().isAnon() && hasType(s.getSubject(), SWRL.DataRangeAtom),
                    null, this::declareDatatype);
            // object property
            processSWRL(SWRL.propertyPredicate,
                    s -> s.getSubject().isAnon() && hasType(s.getSubject(), SWRL.IndividualPropertyAtom),
                    null, this::declareObjectProperty);
            // data property
            processSWRL(SWRL.propertyPredicate,
                    s -> s.getSubject().isAnon() && hasType(s.getSubject(), SWRL.DatavaluedPropertyAtom),
                    null, this::declareDataProperty);
        }

        protected void processSWRL(Property predicateToFind,
                                   Predicate<Statement> functionToFilter,
                                   Predicate<Resource> functionToCheck,
                                   Consumer<Resource> functionToDeclare) {
            statements(null, predicateToFind, null)
                    .filter(functionToFilter)
                    .map(Statement::getObject)
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource)
                    .filter(r -> functionToCheck == null || functionToCheck.test(r))
                    .forEach(functionToDeclare);
        }
    }

    /**
     * The transformer to restore declarations in the implicit cases
     * (all notations are taken from the <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL2 Short Guide</a>):
     * 1) data or object universal or existential quantifications (restrictions):
     * <ul>
     * <li>{@code _:x rdf:type owl:Restriction; owl:onProperty P; owl:allValuesFrom C}</li>
     * <li>{@code _:x rdf:type owl:Restriction; owl:onProperty R; owl:someValuesFrom D}</li>
     * </ul>
     * 2) property domains:
     * <ul>
     * <li>{@code A rdfs:domain U}</li>
     * <li>{@code P rdfs:domain C}</li>
     * <li>{@code R rdfs:domain C}</li>
     * </ul>
     * 3) property ranges:
     * <ul>
     * <li>{@code A rdfs:range U}</li>
     * <li>{@code R rdfs:range D}</li>
     * <li>{@code P rdfs:range C}</li>
     * </ul>
     * 4) property assertions (during reasoning the {@code C owl:hasKey ( P1 ... Pm R1 ... Rn )}
     * and {@code U rdf:type owl:FunctionalProperty} are used):
     * <ul>
     * <li>{@code s A t}</li>
     * <li>{@code a R v}</li>
     * <li>{@code a1 PN a2}</li>
     * </ul>
     * 5) other expression and property constructions where one part could be determined from some another:
     * <ul>
     * <li>{@code C1 owl:equivalentClass C2}</li>
     * <li>{@code DN owl:equivalentClass D}</li>
     * <li>{@code P1 owl:equivalentProperty P2}</li>
     * <li>{@code R1 owl:propertyDisjointWith R2}</li>
     * <li>{@code A1 rdfs:subPropertyOf A2}</li>
     * <li>{@code P1 rdfs:subPropertyOf P2}</li>
     * <li>{@code R1 rdfs:subPropertyOf R2}</li>
     * <li>{@code _:x owl:unionOf ( D1 ... Dn )}</li>
     * <li>{@code _:x owl:intersectionOf ( C1 ... Cn )}</li>
     * <li>{@code _:x rdf:type owl:AllDisjointProperties; owl:members ( P1 ... Pn )}</li>
     * </ul>
     * <p>
     * Note: ObjectProperty &amp; ClassExpression have more priority then DataProperty &amp; DataRange
     */
    @SuppressWarnings("WeakerAccess")
    public static class ReasonerDeclarator extends BaseDeclarator {
        protected static final int MAX_TAIL_COUNT = 10;
        protected static final boolean PREFER_ANNOTATIONS_IN_UNCLEAR_CASES_DEFAULT = true;
        public Map<Statement, Function<Statement, Res>> rerun = new LinkedHashMap<>();

        protected boolean annotationsOpt;

        /**
         * base constructor.
         *
         * @param graph          {@link Graph}
         * @param annotationsOpt if true then it chooses annotation property in unclear cases.
         */
        public ReasonerDeclarator(Graph graph, boolean annotationsOpt) {
            super(graph);
            this.annotationsOpt = annotationsOpt;
        }

        protected ReasonerDeclarator(Graph graph) {
            this(graph, PREFER_ANNOTATIONS_IN_UNCLEAR_CASES_DEFAULT);
        }

        @Override
        public Stream<Statement> statements(Resource s, Property p, RDFNode o) {
            return super.statements(s, p, o).sorted(Models.STATEMENT_COMPARATOR_IGNORE_BLANK);
        }

        @Override
        public void perform() {
            try {
                parseDataAndObjectRestrictions();
                parsePropertyDomains();
                parsePropertyRanges();
                parsePropertyAssertions();

                parseEquivalentClasses();
                parseUnionAndIntersectionClassExpressions();
                parseEquivalentAndDisjointProperties();
                parseAllDisjointProperties();
                parseSubProperties();

                parseTail();
            } finally { // possibility to rerun
                rerun = new LinkedHashMap<>();
            }
        }

        public void parseDataAndObjectRestrictions() {
            // "_:x rdf:type owl:Restriction; owl:onProperty P; owl:allValuesFrom C" and
            // "_:x rdf:type owl:Restriction; owl:onProperty R; owl:someValuesFrom D"
            Stream.of(OWL.allValuesFrom, OWL.someValuesFrom)
                    .map(p -> statements(null, p, null)) // add sorting to process punnings in restrictions
                    .flatMap(Function.identity()).forEach(s -> {
                if (Res.UNKNOWN.equals(dataAndObjectRestrictions(s))) {
                    rerun.put(s, this::dataAndObjectRestrictions);
                }
            });
        }

        public Res dataAndObjectRestrictions(Statement statement) {
            Resource p = getObjectResource(statement.getSubject(), OWL.onProperty);
            Resource c = getObjectResource(statement.getSubject(), statement.getPredicate());
            if (p == null || c == null) {
                return Res.FALSE;
            }
            declare(statement.getSubject(), OWL.Restriction);
            if (isClassExpression(c) || isObjectPropertyExpression(p)) {
                declareObjectProperty(p);
                if (declareClass(c))
                    return Res.TRUE;
            }
            if (isDataRange(c) || isDataProperty(p)) {
                declareDataProperty(p);
                declareDatatype(c);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parsePropertyDomains() {
            // "P rdfs:domain C" or "R rdfs:domain C" or "A rdfs:domain U"
            statements(null, RDFS.domain, null)
                    .filter(s -> s.getObject().isResource()).forEach(s -> {
                if (Res.UNKNOWN.equals(propertyDomains(s, false))) {
                    rerun.put(s, statement -> propertyDomains(statement, annotationsOpt));
                }
            });
        }

        public Res propertyDomains(Statement statement, boolean preferAnnotationsInUnknownCases) {
            Resource left = statement.getSubject();
            Resource right = statement.getObject().asResource();
            if (isAnnotationProperty(left) && right.isURIResource()) {
                return Res.TRUE;
            }
            if (isDataProperty(left) || isObjectPropertyExpression(left)) {
                if (declareClass(right))
                    return Res.TRUE;
            }
            if (right.isAnon()) {
                declareClass(right);
            }
            if (preferAnnotationsInUnknownCases) {
                declareAnnotationProperty(left);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parsePropertyRanges() {
            // "P rdfs:range C" or "R rdfs:range D" or "A rdfs:range U"
            statements(null, RDFS.range, null)
                    .filter(s -> s.getObject().isResource()).forEach(s -> {
                if (Res.UNKNOWN.equals(propertyRanges(s, false))) {
                    rerun.put(s, statement -> propertyRanges(statement, annotationsOpt));
                }
            });
        }

        public Res propertyRanges(Statement statement, boolean preferAnnotationsInUnknownCases) {
            Resource left = statement.getSubject();
            Resource right = statement.getObject().asResource();
            if (isAnnotationProperty(left) && right.isURIResource()) {
                // "A rdfs:range U"
                return Res.TRUE;
            }
            if (isClassExpression(right)) {
                // "P rdfs:range C"
                declareObjectProperty(left);
                return Res.TRUE;
            }
            if (isDataRange(right)) {
                // "R rdfs:range D"
                declareDataProperty(left);
                return Res.TRUE;
            }
            if (preferAnnotationsInUnknownCases) {
                declareAnnotationProperty(left);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parsePropertyAssertions() {
            // "a1 PN a2", "a R v", "s A t"
            Set<Statement> statements = statements(null, null, null)
                    .filter(s -> !builtIn.reservedProperties().contains(s.getPredicate())).collect(Collectors.toSet());
            statements.forEach(s -> {
                if (Res.UNKNOWN.equals(propertyAssertions(s, false))) {
                    rerun.put(s, statement -> propertyAssertions(statement, annotationsOpt));
                }
            });
        }

        public Res propertyAssertions(Statement statement, boolean preferAnnotationsInUnknownCases) {
            Resource subject = statement.getSubject();
            RDFNode right = statement.getObject();
            Property property = statement.getPredicate();
            if (isAnnotationProperty(property)) { // annotation assertion "s A t"
                return Res.TRUE;
            }
            if (right.isLiteral()) { // data property assertion ("a R v")
                if (isDataProperty(property)) {
                    declareIndividual(subject);
                    return Res.TRUE;
                }
                if (isIndividual(subject) && couldBeDataPropertyInAssertion(property)) {
                    declareDataProperty(property);
                    return Res.TRUE;
                }
                if (mustBeDataOrObjectProperty(property)) {
                    declareDataProperty(property);
                    declareIndividual(subject);
                    return Res.TRUE;
                }
            } else {
                Resource object = right.asResource();
                if (isIndividual(object) || couldBeIndividual(object)) {  // object property assertion ("a1 PN a2")
                    if (isObjectPropertyExpression(property)) {
                        declareIndividual(subject);
                        declareIndividual(object);
                        return Res.TRUE;
                    }
                    if (isIndividual(subject)) {
                        declareObjectProperty(property);
                        declareIndividual(object);
                        return Res.TRUE;
                    }
                    if (mustBeDataOrObjectProperty(property)) {
                        declareObjectProperty(property);
                        declareIndividual(subject);
                        declareIndividual(object);
                        return Res.TRUE;
                    }
                }
            }
            if (preferAnnotationsInUnknownCases) {
                declareAnnotationProperty(property);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        protected boolean mustBeDataOrObjectProperty(Resource candidate) {
            // "P rdf:type owl:FunctionalProperty", "R rdf:type owl:FunctionalProperty"
            if (candidate.hasProperty(RDF.type, OWL.FunctionalProperty)) return true;
            // "C owl:hasKey (P1 ... Pm R1 ... Rn)"
            return statements(null, OWL.hasKey, null)
                    .map(Statement::getObject)
                    .filter(o -> o.canAs(RDFList.class))
                    .map(o -> o.as(RDFList.class))
                    .flatMap(l -> Iter.asStream(l.iterator()))
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource)
                    .anyMatch(candidate::equals);
        }

        protected boolean couldBeIndividual(RDFNode candidate) {
            return candidate.isResource() &&
                    (candidate.isAnon() ? !candidate.canAs(RDFList.class) : !builtIn.reserved().contains(candidate.asResource()));
        }

        protected boolean couldBeDataPropertyInAssertion(Property candidate) {
            Set<RDFNode> objects = statements(null, candidate, null).map(Statement::getObject).collect(Collectors.toSet());
            if (objects.stream().anyMatch(RDFNode::isResource)) {
                return true;
            }
            List<String> datatypes = objects.stream().map(RDFNode::asLiteral).map(Literal::getDatatypeURI).distinct().collect(Collectors.toList());
            return datatypes.size() > 1 || !XSD.xstring.getURI().equals(datatypes.get(0));
        }

        public void parseEquivalentClasses() {
            // "C1 owl:equivalentClass C2" and "DN owl:equivalentClass D"
            statements(null, OWL.equivalentClass, null)
                    .filter(s -> s.getObject().isResource())
                    .forEach(s -> {
                        if (Res.UNKNOWN.equals(equivalentClasses(s))) {
                            rerun.put(s, this::equivalentClasses);
                        }
                    });
        }

        public Res equivalentClasses(Statement statement) {
            Resource a = statement.getSubject();
            Resource b = statement.getObject().asResource();
            if (Stream.of(a, b).anyMatch(this::isClassExpression)) {
                declareClass(a);
                declareClass(b);
                return Res.TRUE;
            }
            if (a.isURIResource() && Stream.of(a, b).anyMatch(this::isDataRange)) {
                declareDatatype(a);
                declareDatatype(b);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parseUnionAndIntersectionClassExpressions() {
            // "_:x owl:unionOf ( D1 ... Dn )", "_:x owl:intersectionOf ( C1 ... Cn )"
            Stream.of(OWL.unionOf, OWL.intersectionOf).map(p -> statements(null, p, null))
                    .flatMap(Function.identity())
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getObject().canAs(RDFList.class))
                    .forEach(s -> {
                        if (Res.UNKNOWN.equals(unionAndIntersectionClassExpressions(s))) {
                            rerun.put(s, this::unionAndIntersectionClassExpressions);
                        }
                    });
        }

        public Res unionAndIntersectionClassExpressions(Statement statement) {
            Set<Resource> set = subjectAndObjectsAsSet(statement);
            if (set.stream().anyMatch(this::isClassExpression)) {
                set.forEach(this::declareClass);
                return Res.TRUE;
            }
            if (set.stream().anyMatch(this::isDataRange)) {
                set.forEach(this::declareDatatype);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parseEquivalentAndDisjointProperties() {
            Stream.of(OWL.equivalentProperty, OWL.propertyDisjointWith)
                    .map(p -> statements(null, p, null))
                    .flatMap(Function.identity())
                    .filter(s -> s.getObject().isResource())
                    .forEach(s -> {
                        if (Res.UNKNOWN.equals(equivalentAndDisjointProperties(s))) {
                            rerun.put(s, this::equivalentAndDisjointProperties);
                        }
                    });
        }

        public Res equivalentAndDisjointProperties(Statement statement) {
            Resource a = statement.getSubject();
            Resource b = statement.getObject().asResource();
            if (Stream.of(a, b).anyMatch(this::isObjectPropertyExpression)) {
                declareObjectProperty(a, builtIn.properties());
                declareObjectProperty(b, builtIn.properties());
                return Res.TRUE;
            }
            if (Stream.of(a, b).anyMatch(this::isDataProperty)) {
                declareDataProperty(a, builtIn.properties());
                declareDataProperty(b, builtIn.properties());
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parseAllDisjointProperties() {
            // "_:x rdf:type owl:AllDisjointProperties; owl:members ( P1 ... Pn )"
            statements(null, RDF.type, OWL.AllDisjointProperties)
                    .filter(s -> s.getSubject().isAnon())
                    .filter(s -> s.getSubject().hasProperty(OWL.members))
                    .forEach(s -> {
                        if (Res.UNKNOWN.equals(allDisjointProperties(s))) {
                            rerun.put(s, this::allDisjointProperties);
                        }
                    });
        }

        public Res allDisjointProperties(Statement statement) {
            Set<Resource> set = members(statement.getSubject(), OWL.members).collect(Collectors.toSet());
            if (set.isEmpty()) {
                return Res.FALSE;
            }
            if (set.stream().anyMatch(this::isObjectPropertyExpression)) {
                set.forEach(this::declareObjectProperty);
                return Res.TRUE;
            }
            if (set.stream().anyMatch(this::isDataProperty)) {
                set.forEach(this::declareDataProperty);
                return Res.TRUE;
            }
            return Res.UNKNOWN;
        }

        public void parseSubProperties() {
            statements(null, RDFS.subPropertyOf, null).filter(s -> s.getObject().isResource())
                    .forEach(s -> {
                        if (Res.UNKNOWN.equals(subProperties(s, false))) {
                            rerun.put(s, statement -> subProperties(statement, annotationsOpt));
                        }
                    });
        }

        public Res subProperties(Statement statement, boolean preferAnnotationsInUnknownCases) {
            Resource a = statement.getSubject();
            Resource b = statement.getObject().asResource();
            Res res = Res.UNKNOWN;
            if (Stream.of(a, b).anyMatch(this::isObjectPropertyExpression)) {
                declareObjectProperty(a, builtIn.properties());
                declareObjectProperty(b, builtIn.properties());
                res = Res.TRUE;
            }
            if (Stream.of(a, b).anyMatch(this::isDataProperty)) {
                declareDataProperty(a, builtIn.properties());
                declareDataProperty(b, builtIn.properties());
                res = Res.TRUE;
            }
            if (Stream.of(a, b).anyMatch(this::isAnnotationProperty) ||
                    (Res.UNKNOWN.equals(res) && preferAnnotationsInUnknownCases)) {
                declareAnnotationProperty(a, builtIn.properties());
                declareAnnotationProperty(b, builtIn.properties());
                res = Res.TRUE;
            }
            return res;
        }

        @SuppressWarnings("UnusedReturnValue")
        public Set<Statement> parseTail() {
            Map<Statement, Function<Statement, Res>> prev = new LinkedHashMap<>(rerun);
            Map<Statement, Function<Statement, Res>> next = new LinkedHashMap<>();
            int count = 0;
            while (count++ < MAX_TAIL_COUNT) {
                for (Statement s : prev.keySet()) {
                    Function<Statement, Res> func = prev.get(s);
                    if (Res.UNKNOWN.equals(func.apply(s))) {
                        next.put(s, prev.get(s));
                    }
                }
                if (next.isEmpty()) return Collections.emptySet();
                if (next.size() == prev.size()) {
                    break;
                }
                prev = next;
                next = new LinkedHashMap<>();
            }
            LOGGER.warn("Ambiguous statements " + next.keySet());
            return next.keySet();
        }

        public enum Res {
            TRUE,
            FALSE,
            UNKNOWN,
        }
    }

    /**
     * The collection of base methods for {@link ManifestDeclarator} and {@link ReasonerDeclarator}
     */
    @SuppressWarnings("WeakerAccess")
    public static abstract class BaseDeclarator extends Transform {
        protected BaseDeclarator(Graph graph) {
            super(graph);
        }

        protected Set<Resource> subjectAndObjectsAsSet(Statement s) {
            Set<Resource> res = new HashSet<>();
            res.add(s.getSubject());
            Iter.asStream(s.getObject().as(RDFList.class).iterator())
                    .filter(RDFNode::isResource).map(RDFNode::asResource).forEach(res::add);
            return res;
        }

        public Stream<Resource> members(Resource subject, Property predicate) {
            return Iter.asStream(subject.listProperties(predicate))
                    .map(Statement::getObject)
                    .filter(o -> o.canAs(RDFList.class))
                    .map(r -> r.as(RDFList.class))
                    .flatMap(l -> Iter.asStream(l.iterator()))
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource);
        }

        public Resource getObjectResource(Resource subject, Property predicate) {
            Statement res = subject.getProperty(predicate);
            return res != null && res.getObject().isResource() ? res.getObject().asResource() : null;
        }

        public Literal getObjectLiteral(Resource subject, Property predicate) {
            Statement res = subject.getProperty(predicate);
            return res != null && res.getObject().isLiteral() ? res.getObject().asLiteral() : null;
        }

        public boolean isClassExpression(Resource candidate) {
            return builtIn.classes().contains(candidate) || hasType(candidate, OWL.Class) || hasType(candidate, OWL.Restriction);
        }

        public boolean isDataRange(Resource candidate) {
            return builtIn.datatypes().contains(candidate) || hasType(candidate, RDFS.Datatype);
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        public boolean isObjectPropertyExpression(Resource candidate) {
            return builtIn.objectProperties().contains(candidate)
                    || hasType(candidate, OWL.ObjectProperty)
                    || candidate.hasProperty(OWL.inverseOf);
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        public boolean isDataProperty(Resource candidate) {
            return builtIn.datatypeProperties().contains(candidate) || hasType(candidate, OWL.DatatypeProperty);
        }

        @SuppressWarnings("SuspiciousMethodCalls")
        public boolean isAnnotationProperty(Resource candidate) {
            return builtIn.annotationProperties().contains(candidate) || hasType(candidate, OWL.AnnotationProperty);
        }

        public boolean isIndividual(Resource candidate) {
            return hasType(candidate, OWL.NamedIndividual) || hasType(candidate, AVC.AnonymousIndividual);
        }

        public void declareObjectProperty(Resource resource) {
            declareObjectProperty(resource, builtIn.objectProperties());
        }

        public void declareDataProperty(Resource resource) {
            declareDataProperty(resource, builtIn.datatypeProperties());
        }

        public void declareAnnotationProperty(Resource resource) {
            declareAnnotationProperty(resource, builtIn.annotationProperties());
        }

        public void declareObjectProperty(Resource resource, Set<? extends Resource> builtIn) {
            if (resource.isAnon()) {
                undeclare(resource, OWL.ObjectProperty);
                return;
            }
            declare(resource, OWL.ObjectProperty, builtIn);
        }

        public void declareDataProperty(Resource resource, Set<? extends Resource> builtIn) {
            declare(resource, OWL.DatatypeProperty, builtIn);
        }

        public void declareAnnotationProperty(Resource resource, Set<? extends Resource> builtIn) {
            declare(resource, OWL.AnnotationProperty, builtIn);
        }

        public void declareIndividual(Resource resource) {
            if (resource.isAnon()) {
                // test data from owl-api-contact contains such things also:
                undeclare(resource, OWL.NamedIndividual);
                // the temporary declaration:
                declare(resource, AVC.AnonymousIndividual);
            } else {
                declare(resource, OWL.NamedIndividual);
            }
        }

        public void declareDatatype(Resource resource) {
            declare(resource, RDFS.Datatype, builtIn.datatypes());
        }

        public boolean declareClass(Resource resource) {
            if (builtIn.classes().contains(resource)) {
                return true;
            }
            Resource type = resource.isURIResource() ? OWL.Class :
                    containsClassExpressionProperty(resource) ? OWL.Class :
                            containsRestrictionProperty(resource) ? OWL.Restriction : null;
            if (type != null) {
                declare(resource, type);
                return true;
            }
            return false;
        }

        public boolean containsClassExpressionProperty(Resource candidate) {
            return Stream.of(OWL.intersectionOf, OWL.oneOf, OWL.unionOf, OWL.complementOf).anyMatch(candidate::hasProperty);
        }

        public boolean containsRestrictionProperty(Resource candidate) {
            return Stream.of(OWL.onProperty, OWL.allValuesFrom,
                    OWL.someValuesFrom, OWL.hasValue, OWL.onClass,
                    OWL.onDataRange, OWL.cardinality, OWL.qualifiedCardinality,
                    OWL.maxCardinality, OWL.maxQualifiedCardinality, OWL.minCardinality,
                    OWL.maxQualifiedCardinality, OWL.onProperties).anyMatch(candidate::hasProperty);
        }

        public void declare(Resource subject, Resource type, Set<? extends Resource> forbidden) {
            if (type == null || forbidden.contains(subject)) {
                return;
            }
            declare(subject, type);
        }

    }

}
