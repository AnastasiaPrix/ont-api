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
 *
 */

package ru.avicomp.ontapi.internal;

import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLAPIStreamUtils;
import org.semanticweb.owlapi.vocab.OWLFacet;
import ru.avicomp.ontapi.OntApiException;
import ru.avicomp.ontapi.config.OntLoaderConfiguration;
import ru.avicomp.ontapi.jena.impl.OntObjectImpl;
import ru.avicomp.ontapi.jena.model.*;
import ru.avicomp.ontapi.jena.utils.Models;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper to translate rdf-graph to the owl-objects form.
 * <p>
 * Created by @szuev on 25.11.2016.
 */
@SuppressWarnings("WeakerAccess")
public class ReadHelper {

    /**
     * @param entity {@link OntEntity}
     * @param df     {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLEntity}
     */
    protected static InternalObject<? extends OWLEntity> wrapEntity(OntEntity entity, OWLDataFactory df) {
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) entity).getActualClass(),
                "Can't determine view of entity " + entity);
        if (OntClass.class.equals(view)) {
            return fetchClass((OntClass) entity, df);
        } else if (OntDT.class.equals(view)) {
            return fetchDatatype((OntDT) entity, df);
        } else if (OntIndividual.Named.class.equals(view)) {
            return fetchNamedIndividual((OntIndividual.Named) entity, df);
        } else if (OntNAP.class.equals(view)) {
            return fetchAnnotationProperty((OntNAP) entity, df);
        } else if (OntNDP.class.equals(view)) {
            return fetchDataProperty((OntNDP) entity, df);
        } else if (OntNOP.class.equals(view)) {
            return fetchObjectProperty((OntNOP) entity, df);
        }
        throw new OntApiException("Unsupported " + entity);
    }

    public static InternalObject<? extends OWLClassExpression> fetchClassExpression(OntCE ce, OWLDataFactory df) {
        return ce.getModel() instanceof InternalModel ? ((InternalModel) ce.getModel()).fetchClassExpression(ce) : getClassExpression(ce, df);
    }

    public static InternalObject<? extends OWLDataRange> fetchDataRange(OntDR dr, OWLDataFactory df) {
        return dr.getModel() instanceof InternalModel ? ((InternalModel) dr.getModel()).fetchDataRange(dr) : getDataRange(dr, df);
    }

    public static InternalObject<? extends OWLIndividual> fetchIndividual(OntIndividual indi, OWLDataFactory df) {
        return indi.getModel() instanceof InternalModel ? ((InternalModel) indi.getModel()).fetchIndividual(indi) : getIndividual(indi, df);
    }

    public static InternalObject<OWLAnnotationProperty> fetchAnnotationProperty(OntNAP nap, OWLDataFactory df) {
        return nap.getModel() instanceof InternalModel ? ((InternalModel) nap.getModel()).fetchAnnotationProperty(nap) : getAnnotationProperty(nap, df);
    }

    public static InternalObject<OWLDataProperty> fetchDataProperty(OntNDP ndp, OWLDataFactory df) {
        return ndp.getModel() instanceof InternalModel ? ((InternalModel) ndp.getModel()).fetchDataProperty(ndp) : getDataProperty(ndp, df);
    }

    public static InternalObject<? extends OWLObjectPropertyExpression> fetchObjectPropertyExpression(OntOPE ope, OWLDataFactory df) {
        return ope.getModel() instanceof InternalModel ? ((InternalModel) ope.getModel()).fetchObjectProperty(ope) : getObjectPropertyExpression(ope, df);
    }

    /**
     * @param individual {@link OntIndividual.Named}
     * @param df         {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLNamedIndividual}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<OWLNamedIndividual> fetchNamedIndividual(OntIndividual.Named individual, OWLDataFactory df) {
        return (InternalObject<OWLNamedIndividual>) fetchIndividual(individual, df);
    }

    /**
     * @param anon {@link OntIndividual.Anonymous}
     * @param df   {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLAnonymousIndividual}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<OWLAnonymousIndividual> getAnonymousIndividual(OntIndividual.Anonymous anon, OWLDataFactory df) {
        return (InternalObject<OWLAnonymousIndividual>) fetchIndividual(anon, df);
    }

    /**
     * @param individual {@link OntIndividual}
     * @param df         {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLIndividual}
     */
    public static InternalObject<? extends OWLIndividual> getIndividual(OntIndividual individual, OWLDataFactory df) {
        if (OntApiException.notNull(individual, "Null individual").isURIResource()) {
            return InternalObject.create(df.getOWLNamedIndividual(IRI.create(individual.getURI())), individual);
        }
        String label = //NodeFmtLib.encodeBNodeLabel(individual.asNode().getBlankNodeLabel());
                individual.asNode().getBlankNodeLabel();
        return InternalObject.create(df.getOWLAnonymousIndividual(label), individual);
    }

    /**
     * NOTE: THE VIOLATION OF THE FUNDAMENTAL JAVA CONTRACT (OWL-API-impl 5.1.4):
     * The different implementations of {@link OWLLiteral} have different mechanism to calculate hash.
     * For example <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLLiteralImplInteger.java'>uk.ac.manchester.cs.owl.owlapi.OWLLiteralImplInteger</a>.hashCode
     * != <a href='https://github.com/owlcs/owlapi/blob/version5/impl/src/main/java/uk/ac/manchester/cs/owl/owlapi/OWLLiteralImpl.java'>uk.ac.manchester.cs.owl.owlapi.OWLLiteralImpl</a>.hashCode
     * So even if {@link OWLLiteral}s equal there is no guarantee that {@link Set}s of {@link OWLLiteral}s equal too.
     *
     * @param literal {@link Literal}
     * @param df      {@link OWLDataFactory}
     * @return {@link InternalObject}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<OWLLiteral> getLiteral(Literal literal, OWLDataFactory df) {
        String txt = OntApiException.notNull(literal, "Null literal").getLexicalForm();
        String lang = literal.getLanguage();
        if (lang != null && !lang.isEmpty()) {
            txt = txt + "@" + lang;
        }
        OntDT dt = literal.getModel().getResource(literal.getDatatypeURI()).as(OntDT.class);
        InternalObject<OWLDatatype> owl;
        if (dt.isBuiltIn()) {
            owl = InternalObject.create(df.getOWLDatatype(IRI.create(dt.getURI())));
        } else {
            owl = (InternalObject<OWLDatatype>) getDataRange(dt, df);
        }
        OWLLiteral res = df.getOWLLiteral(txt, owl.getObject());
        return InternalObject.create(res).append(owl);
    }

    public static InternalObject<IRI> wrapIRI(OntObject object) {
        return InternalObject.create(IRI.create(object.getURI()), object.canAs(OntEntity.class) ? object.as(OntEntity.class) : object);
    }

    /**
     * @param resource {@link OntObject}
     * @param df       {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLAnnotationSubject}
     */
    public static InternalObject<? extends OWLAnnotationSubject> getAnnotationSubject(OntObject resource, OWLDataFactory df) {
        if (OntApiException.notNull(resource, "Null resource").isURIResource()) {
            return wrapIRI(resource);
        }
        if (resource.isAnon()) {
            return getAnonymousIndividual(Models.asAnonymousIndividual(resource), df);
        }
        throw new OntApiException("Not an AnnotationSubject " + resource);
    }

    /**
     * @param node {@link RDFNode}
     * @param df   {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLAnnotationValue}
     */
    public static InternalObject<? extends OWLAnnotationValue> getAnnotationValue(RDFNode node, OWLDataFactory df) {
        if (OntApiException.notNull(node, "Null node").isLiteral()) {
            return getLiteral(node.asLiteral(), df);
        }
        if (node.isURIResource()) {
            return wrapIRI(node.as(OntObject.class));
        }
        if (node.isAnon()) {
            return getAnonymousIndividual(Models.asAnonymousIndividual(node), df);
        }
        throw new OntApiException("Not an AnnotationValue " + node);
    }

    /**
     * @param property {@link OntPE}
     * @param df       {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLPropertyExpression}
     */
    public static InternalObject<? extends OWLPropertyExpression> getProperty(OntPE property, OWLDataFactory df) {
        if (OntApiException.notNull(property, "Null property.").canAs(OntNAP.class)) {
            return fetchAnnotationProperty(property.as(OntNAP.class), df);
        }
        if (property.canAs(OntNDP.class)) {
            return fetchDataProperty(property.as(OntNDP.class), df);
        }
        if (property.canAs(OntOPE.class)) {
            return fetchObjectPropertyExpression(property.as(OntOPE.class), df);
        }
        throw new OntApiException("Unsupported property " + property);
    }

    /**
     * @param nap {@link OntNAP}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLAnnotationProperty}
     */
    public static InternalObject<OWLAnnotationProperty> getAnnotationProperty(OntNAP nap, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(nap, "Null annotation property.").getURI());
        return InternalObject.create(df.getOWLAnnotationProperty(iri), nap);
    }

    /**
     * @param ndp {@link OntNDP}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLDataProperty}
     */
    public static InternalObject<OWLDataProperty> getDataProperty(OntNDP ndp, OWLDataFactory df) {
        IRI iri = IRI.create(OntApiException.notNull(ndp, "Null data property.").getURI());
        return InternalObject.create(df.getOWLDataProperty(iri), ndp);
    }

    @SuppressWarnings("unchecked")
    public static InternalObject<OWLObjectProperty> fetchObjectProperty(OntNOP nop, OWLDataFactory df) {
        return (InternalObject<OWLObjectProperty>) fetchObjectPropertyExpression(nop, df);
    }

    /**
     * @param ope {@link OntOPE}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLObjectPropertyExpression}
     */
    public static InternalObject<? extends OWLObjectPropertyExpression> getObjectPropertyExpression(OntOPE ope, OWLDataFactory df) {
        OntApiException.notNull(ope, "Null object property.");
        if (ope.isAnon()) { //todo: handle inverse of inverseOf (?)
            OWLObjectProperty op = df.getOWLObjectProperty(IRI.create(ope.as(OntOPE.Inverse.class).getDirect().getURI()));
            return InternalObject.create(op.getInverseProperty(), ope);
        }
        return InternalObject.create(df.getOWLObjectProperty(IRI.create(ope.getURI())), ope);
    }

    /**
     * @param dt {@link OntDT}
     * @param df {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLDatatype}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<OWLDatatype> fetchDatatype(OntDT dt, OWLDataFactory df) {
        return (InternalObject<OWLDatatype>) fetchDataRange(dt, df);
    }

    /**
     * Auxiliary method for simplification code.
     * Used in Annotation Translators.
     * If the specified statement also belongs to the another type of axiom and it is prohibited in the config then returns false.
     * This is for three kinds of statements:
     * - "A1 rdfs:subPropertyOf A2"
     * - "A rdfs:domain U"
     * - "A rdfs:range U"
     * Each of them is wider than the analogous statement for object or data property,
     * e.g. "P rdfs:range C" could be treated as "A rdfs:range U", but not vice versa.
     *
     * @param statement {@link OntStatement} to test
     * @param conf      {@link OntLoaderConfiguration}
     * @param o         {@link AxiomType#SUB_OBJECT_PROPERTY} or {@link AxiomType#OBJECT_PROPERTY_DOMAIN} or {@link AxiomType#OBJECT_PROPERTY_RANGE}
     * @param d         {@link AxiomType#SUB_DATA_PROPERTY} or {@link AxiomType#DATA_PROPERTY_DOMAIN} or {@link AxiomType#DATA_PROPERTY_RANGE}
     * @return true if the statement is good to be represented in the form of annotation axiom.
     */
    protected static boolean testAnnotationAxiomOverlaps(OntStatement statement,
                                                         OntLoaderConfiguration conf,
                                                         AxiomType<? extends OWLObjectPropertyAxiom> o,
                                                         AxiomType<? extends OWLDataPropertyAxiom> d) {
        return conf == null || !(conf.isIgnoreAnnotationAxiomOverlaps() &&
                Stream.of(d, o).map(AxiomParserProvider::get).anyMatch(a -> a.testStatement(statement)));
    }

    public static boolean isDeclarationStatement(OntStatement statement) {
        return statement.isRoot() && statement.isDeclaration() && isEntityOrAnonymousIndividual(statement.getSubject());
    }

    public static boolean isEntityOrAnonymousIndividual(OntObject subject) {
        return (subject.isURIResource() && !subject.canAs(OntID.class)) || subject.canAs(OntIndividual.Anonymous.class);
    }

    public static boolean isAnnotationAssertionStatement(OntStatement statement, OntLoaderConfiguration conf) {
        return statement.isAnnotation() && !statement.getSubject().canAs(OntAnnotation.class) && (isAllowBulkAnnotationAssertions(conf) || !hasAnnotations(statement));
    }

    public static Stream<OntStatement> annotations(OntStatement statement) {
        return statement.annotations();
    }

    public static boolean hasAnnotations(OntStatement statement) {
        return statement.hasAnnotations();
    }

    /**
     * Returns the container with set of {@link OWLAnnotation} associated with the specified statement.
     *
     * @param stm {@link OntStatement}
     * @param df        {@link OWLDataFactory}
     * @param conf      {@link OntLoaderConfiguration}
     * @return a set of wraps {@link InternalObject} around {@link OWLAnnotation}
     */
    public static Set<InternalObject<OWLAnnotation>> getAnnotations(OntStatement stm, OWLDataFactory df, OntLoaderConfiguration conf) {
        Set<InternalObject<OWLAnnotation>> res = getAllAnnotations(stm, df);
        if (isAnnotationAssertionsAllowed(conf) && isDeclarationStatement(stm)) {
            // for compatibility with OWL-API skip all plain annotations attached to an entity (or anonymous individual)
            // they would go separately as annotation-assertions.
            annotations(stm).filter(s -> isAnnotationAssertionStatement(s, conf))
                    .map(a -> getAnnotation(a, df)).forEach(res::remove);
        }
        return res;
    }

    /**
     * by default annotation axioms are allowed.
     *
     * @param conf {@link OntLoaderConfiguration}
     * @return true if annotation axioms are allowed
     */
    private static boolean isAnnotationAssertionsAllowed(OntLoaderConfiguration conf) {
        return conf == null || conf.isLoadAnnotationAxioms();
    }

    /**
     * by default we prefer bulk annotation assertions rather then annotated declarations.
     *
     * @param conf {@link OntLoaderConfiguration}
     * @return true if bulk assertions are preferable.
     */
    private static boolean isAllowBulkAnnotationAssertions(OntLoaderConfiguration conf) {
        return conf == null || conf.isAllowBulkAnnotationAssertions();
    }

    /**
     * Returns all annotations related to the object (including assertions).
     *
     * @param obj {@link OntObject}
     * @param df  {@link OWLDataFactory}
     * @return a set of {@link InternalObject} for {@link OWLAnnotation}
     */
    public static Set<InternalObject<OWLAnnotation>> getObjectAnnotations(OntObject obj, OWLDataFactory df) {
        return getAllAnnotations(obj.getRoot(), df);
    }

    private static Set<InternalObject<OWLAnnotation>> getAllAnnotations(OntStatement statement, OWLDataFactory df) {
        return annotations(statement).map(a -> getAnnotation(a, df)).collect(Collectors.toSet());
    }

    /**
     * Translates {@link OntStatement} to {@link InternalObject} encapsulated {@link OWLAnnotation}.
     *
     * @param ann {@link OntStatement}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLAnnotation}
     */
    public static InternalObject<OWLAnnotation> getAnnotation(OntStatement ann, OWLDataFactory df) {
        return hasAnnotations(ann) ? getHierarchicalAnnotations(ann, df) : getPlainAnnotation(ann, df);
    }

    private static InternalObject<OWLAnnotation> getPlainAnnotation(OntStatement ann, OWLDataFactory df) {
        InternalObject<OWLAnnotationProperty> p = fetchAnnotationProperty(ann.getPredicate().as(OntNAP.class), df);
        InternalObject<? extends OWLAnnotationValue> v = getAnnotationValue(ann.getObject(), df);
        OWLAnnotation res = df.getOWLAnnotation(p.getObject(), v.getObject(), Stream.empty());
        return InternalObject.create(res, ann).append(p).append(v);
    }

    private static InternalObject<OWLAnnotation> getHierarchicalAnnotations(OntStatement root, OWLDataFactory df) {
        OntObject subject = root.getSubject();
        InternalObject<OWLAnnotationProperty> p = fetchAnnotationProperty(root.getPredicate().as(OntNAP.class), df);
        InternalObject<? extends OWLAnnotationValue> v = getAnnotationValue(root.getObject(), df);
        Set<InternalObject<OWLAnnotation>> children = annotations(root).map(a -> getHierarchicalAnnotations(a, df)).collect(Collectors.toSet());
        OWLAnnotation object = df.getOWLAnnotation(p.getObject(), v.getObject(), children.stream().map(InternalObject::getObject));
        InternalObject<OWLAnnotation> res = InternalObject.create(object, root);
        if (subject.canAs(OntAnnotation.class)) {
            res = res.append(subject);
        }
        return res.append(p).append(v).append(children);
    }

    /**
     * @param fr {@link OntFR}
     * @param df {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLFacetRestriction}
     */
    public static InternalObject<OWLFacetRestriction> getFacetRestriction(OntFR fr, OWLDataFactory df) {
        OWLFacetRestriction res = getOWLFacetRestriction(fr, df);
        return InternalObject.create(res, fr);
    }

    private static OWLFacetRestriction getOWLFacetRestriction(OntFR fr, OWLDataFactory df) {
        OWLLiteral literal = getLiteral(OntApiException.notNull(fr, "Null facet restriction.").getValue(), df).getObject();
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) fr).getActualClass(),
                "Can't determine view of facet restriction " + fr);
        if (OntFR.Length.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.LENGTH, literal);
        if (OntFR.MinLength.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.MIN_LENGTH, literal);
        if (OntFR.MaxLength.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.MAX_LENGTH, literal);
        if (OntFR.MinInclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MIN_INCLUSIVE, literal);
        if (OntFR.MaxInclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MAX_INCLUSIVE, literal);
        if (OntFR.MinExclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MIN_EXCLUSIVE, literal);
        if (OntFR.MaxExclusive.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.MAX_EXCLUSIVE, literal);
        if (OntFR.Pattern.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.PATTERN, literal);
        if (OntFR.FractionDigits.class.equals(view))
            return df.getOWLFacetRestriction(OWLFacet.FRACTION_DIGITS, literal);
        if (OntFR.TotalDigits.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.TOTAL_DIGITS, literal);
        if (OntFR.LangRange.class.equals(view)) return df.getOWLFacetRestriction(OWLFacet.LANG_RANGE, literal);
        throw new OntApiException("Unsupported facet restriction " + fr);
    }

    /**
     * @param dr {@link OntDR}
     * @param df {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLDataRange}
     */
    public static InternalObject<? extends OWLDataRange> getDataRange(OntDR dr, OWLDataFactory df) {
        return getDataRange(dr, df, new HashSet<>());
    }

    /**
     * @param dr   {@link OntDR}
     * @param df   {@link OWLDataFactory}
     * @param seen Set of {@link Resource}
     * @return {@link InternalObject} around {@link OWLDataRange}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<? extends OWLDataRange> getDataRange(OntDR dr, OWLDataFactory df, Set<Resource> seen) {
        OntGraphModel model = dr.getModel();
        if (model instanceof InternalModel) {
            // hack to facilitate recursion call (when this method is invoked from itself, not from InternalModel).
            // todo: this solution is ugly, need to rewrite
            InternalObject<? extends OWLDataRange> res = ((InternalModel) model).temporaryObjects.get(dr, false);
            if (res != null) return res;
        }
        if (OntApiException.notNull(dr, "Null data range.").isAnon() && seen.contains(dr)) {
            throw new OntApiException("Recursive loop on data range " + dr);
        }
        seen.add(dr);
        if (dr.isURIResource()) {
            return InternalObject.create(df.getOWLDatatype(IRI.create(dr.getURI())), dr);
        }
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) dr).getActualClass(),
                "Can't determine view of data range " + dr);
        if (OntDR.Restriction.class.equals(view)) {
            OntDR.Restriction _dr = (OntDR.Restriction) dr;
            InternalObject<OWLDatatype> d = fetchDatatype(_dr.getDatatype(), df);
            Set<InternalObject<OWLFacetRestriction>> restrictions = _dr.facetRestrictions().map(f -> getFacetRestriction(f, df))
                    .collect(Collectors.toSet());
            OWLDataRange res = df.getOWLDatatypeRestriction(d.getObject(), restrictions.stream().map(InternalObject::getObject).collect(Collectors.toList()));
            return InternalObject.create(res, dr).append(restrictions);
        }
        if (OntDR.ComplementOf.class.equals(view)) {
            OntDR.ComplementOf _dr = (OntDR.ComplementOf) dr;
            InternalObject<? extends OWLDataRange> d = getDataRange(_dr.getDataRange(), df, seen);
            return InternalObject.create(df.getOWLDataComplementOf(d.getObject()), _dr).append(d);
        }
        if (OntDR.UnionOf.class.equals(view) ||
                OntDR.IntersectionOf.class.equals(view)) {
            Set<InternalObject<? extends OWLDataRange>> dataRanges =
                    (OntDR.UnionOf.class.equals(view) ? ((OntDR.UnionOf) dr).dataRanges() : ((OntDR.IntersectionOf) dr).dataRanges())
                            .map(d -> getDataRange(d, df, seen))
                            .collect(Collectors.toSet());
            OWLDataRange res = OntDR.UnionOf.class.equals(view) ?
                    df.getOWLDataUnionOf(dataRanges.stream().map(InternalObject::getObject)) :
                    df.getOWLDataIntersectionOf(dataRanges.stream().map(InternalObject::getObject));
            return InternalObject.create(res, dr).appendWildcards(dataRanges);
        }
        if (OntDR.OneOf.class.equals(view)) {
            OntDR.OneOf _dr = (OntDR.OneOf) dr;
            Set<InternalObject<OWLLiteral>> literals = _dr.values().map(v -> getLiteral(v, df)).collect(Collectors.toSet());
            OWLDataRange res = df.getOWLDataOneOf(literals.stream().map(InternalObject::getObject));
            return InternalObject.create(res, _dr);
        }
        throw new OntApiException("Unsupported data range expression " + dr);
    }

    @SuppressWarnings("unchecked")
    public static InternalObject<OWLClass> fetchClass(OntClass cl, OWLDataFactory df) {
        return (InternalObject<OWLClass>) fetchClassExpression(cl, df);
    }

    /**
     * @param ce {@link OntCE}
     * @param df {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link OWLClassExpression}
     */
    public static InternalObject<? extends OWLClassExpression> getClassExpression(OntCE ce, OWLDataFactory df) {
        return getClassExpression(ce, df, new HashSet<>());
    }

    /**
     * @param ce   {@link OntCE}
     * @param df   {@link OWLDataFactory}
     * @param seen Set of {@link Resource}
     * @return {@link InternalObject} around {@link OWLClassExpression}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<? extends OWLClassExpression> getClassExpression(OntCE ce, OWLDataFactory df, Set<Resource> seen) {
        OntGraphModel model = ce.getModel();
        if (model instanceof InternalModel) { // hack to facilitate recursion call. todo: ugly solution
            InternalObject<? extends OWLClassExpression> res = ((InternalModel) model).temporaryObjects.get(ce, false);
            if (res != null) return res;
        }
        if (OntApiException.notNull(ce, "Null class expression.").isAnon() && seen.contains(ce)) {
            throw new OntApiException("Recursive loop on class expression " + ce);
        }
        seen.add(ce);
        if (ce.isURIResource()) {
            return InternalObject.create(df.getOWLClass(IRI.create(ce.getURI())), ce);
        }
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) ce).getActualClass(),
                "Can't determine view of class expression " + ce);
        if (OntCE.ObjectSomeValuesFrom.class.equals(view) ||
                OntCE.ObjectAllValuesFrom.class.equals(view)) {
            OntCE.ComponentRestrictionCE<OntCE, OntOPE> _ce = (OntCE.ComponentRestrictionCE<OntCE, OntOPE>) ce;
            InternalObject<? extends OWLObjectPropertyExpression> p = fetchObjectPropertyExpression(_ce.getOnProperty(), df);
            InternalObject<? extends OWLClassExpression> c = getClassExpression(_ce.getValue(), df, seen);
            OWLClassExpression res;
            if (OntCE.ObjectSomeValuesFrom.class.equals(view))
                res = df.getOWLObjectSomeValuesFrom(p.getObject(), c.getObject());
            else if (OntCE.ObjectAllValuesFrom.class.equals(view))
                res = df.getOWLObjectAllValuesFrom(p.getObject(), c.getObject());
            else
                throw new OntApiException("Should never happen");
            return InternalObject.create(res, _ce).append(p).append(c);
        }
        if (OntCE.DataSomeValuesFrom.class.equals(view) ||
                OntCE.DataAllValuesFrom.class.equals(view)) {
            OntCE.ComponentRestrictionCE<OntDR, OntNDP> _ce = (OntCE.ComponentRestrictionCE<OntDR, OntNDP>) ce;
            InternalObject<OWLDataProperty> p = fetchDataProperty(_ce.getOnProperty(), df);
            InternalObject<? extends OWLDataRange> d = fetchDataRange(_ce.getValue(), df);
            OWLClassExpression res;
            if (OntCE.DataSomeValuesFrom.class.equals(view))
                res = df.getOWLDataSomeValuesFrom(p.getObject(), d.getObject());
            else if (OntCE.DataAllValuesFrom.class.equals(view))
                res = df.getOWLDataAllValuesFrom(p.getObject(), d.getObject());
            else
                throw new OntApiException("Should never happen");
            return InternalObject.create(res, _ce).append(p).append(d);
        }
        if (OntCE.ObjectHasValue.class.equals(view)) {
            OntCE.ObjectHasValue _ce = (OntCE.ObjectHasValue) ce;
            InternalObject<? extends OWLObjectPropertyExpression> p = fetchObjectPropertyExpression(_ce.getOnProperty(), df);
            InternalObject<? extends OWLIndividual> i = fetchIndividual(_ce.getValue(), df);
            return InternalObject.create(df.getOWLObjectHasValue(p.getObject(), i.getObject()), _ce).append(p).append(i);
        }
        if (OntCE.DataHasValue.class.equals(view)) {
            OntCE.DataHasValue _ce = (OntCE.DataHasValue) ce;
            InternalObject<OWLDataProperty> p = fetchDataProperty(_ce.getOnProperty(), df);
            InternalObject<OWLLiteral> l = getLiteral(_ce.getValue(), df);
            return InternalObject.create(df.getOWLDataHasValue(p.getObject(), l.getObject()), _ce).append(p);
        }
        if (OntCE.ObjectMinCardinality.class.equals(view) ||
                OntCE.ObjectMaxCardinality.class.equals(view) ||
                OntCE.ObjectCardinality.class.equals(view)) {
            OntCE.CardinalityRestrictionCE<OntCE, OntOPE> _ce = (OntCE.CardinalityRestrictionCE<OntCE, OntOPE>) ce;
            InternalObject<? extends OWLObjectPropertyExpression> p = fetchObjectPropertyExpression(_ce.getOnProperty(), df);
            InternalObject<? extends OWLClassExpression> c = getClassExpression(_ce.getValue() == null ? _ce.getModel().getOWLThing() : _ce.getValue(), df, seen);
            OWLObjectCardinalityRestriction res;
            if (OntCE.ObjectMinCardinality.class.equals(view))
                res = df.getOWLObjectMinCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else if (OntCE.ObjectMaxCardinality.class.equals(view))
                res = df.getOWLObjectMaxCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else if (OntCE.ObjectCardinality.class.equals(view))
                res = df.getOWLObjectExactCardinality(_ce.getCardinality(), p.getObject(), c.getObject());
            else
                throw new OntApiException("Should never happen");
            return InternalObject.create(res, _ce).append(p).append(c);
        }
        if (OntCE.DataMinCardinality.class.equals(view) ||
                OntCE.DataMaxCardinality.class.equals(view) ||
                OntCE.DataCardinality.class.equals(view)) {
            OntCE.CardinalityRestrictionCE<OntDR, OntNDP> _ce = (OntCE.CardinalityRestrictionCE<OntDR, OntNDP>) ce;
            InternalObject<OWLDataProperty> p = fetchDataProperty(_ce.getOnProperty(), df);
            InternalObject<? extends OWLDataRange> d = fetchDataRange(_ce.getValue() == null ? _ce.getModel().getRDFSLiteral() : _ce.getValue(), df);
            OWLDataCardinalityRestriction res;
            if (OntCE.DataMinCardinality.class.equals(view))
                res = df.getOWLDataMinCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else if (OntCE.DataMaxCardinality.class.equals(view))
                res = df.getOWLDataMaxCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else if (OntCE.DataCardinality.class.equals(view))
                res = df.getOWLDataExactCardinality(_ce.getCardinality(), p.getObject(), d.getObject());
            else
                throw new OntApiException("Should never happen");
            return InternalObject.create(res, _ce).append(p).append(d);
        }
        if (OntCE.HasSelf.class.equals(view)) {
            OntCE.HasSelf _ce = (OntCE.HasSelf) ce;
            InternalObject<? extends OWLObjectPropertyExpression> p = fetchObjectPropertyExpression(_ce.getOnProperty(), df);
            return InternalObject.create(df.getOWLObjectHasSelf(p.getObject()), _ce).append(p);
        }
        if (OntCE.UnionOf.class.equals(view) ||
                OntCE.IntersectionOf.class.equals(view)) {
            OntCE.ComponentsCE<OntCE> _ce = (OntCE.ComponentsCE<OntCE>) ce;
            Set<InternalObject<? extends OWLClassExpression>> components = _ce.components()
                    .map(c -> getClassExpression(c, df, seen)).collect(Collectors.toSet());
            OWLClassExpression res;
            if (OntCE.UnionOf.class.equals(view))
                res = df.getOWLObjectUnionOf(components.stream().map(InternalObject::getObject));
            else if (OntCE.IntersectionOf.class.equals(view))
                res = df.getOWLObjectIntersectionOf(components.stream().map(InternalObject::getObject));
            else
                throw new OntApiException("Should never happen");
            return InternalObject.create(res, _ce).appendWildcards(components);
        }
        if (OntCE.OneOf.class.equals(view)) {
            OntCE.OneOf _ce = (OntCE.OneOf) ce;
            Set<InternalObject<? extends OWLIndividual>> components = _ce.components()
                    .map(c -> fetchIndividual(c, df)).collect(Collectors.toSet());
            OWLClassExpression res = df.getOWLObjectOneOf(components.stream().map(InternalObject::getObject));
            return InternalObject.create(res, _ce).appendWildcards(components);
        }
        if (OntCE.ComplementOf.class.isInstance(ce)) {
            OntCE.ComplementOf _ce = (OntCE.ComplementOf) ce;
            InternalObject<? extends OWLClassExpression> c = getClassExpression(_ce.getValue(), df, seen);
            return InternalObject.create(df.getOWLObjectComplementOf(c.getObject()), _ce).append(c);
        }
        throw new OntApiException("Unsupported class expression " + ce);
    }

    /**
     * @param var {@link OntSWRL.Variable}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link SWRLVariable}
     */
    public static InternalObject<SWRLVariable> getSWRLVariable(OntSWRL.Variable var, OWLDataFactory df) {
        if (!OntApiException.notNull(var, "Null swrl var").isURIResource()) {
            throw new OntApiException("Anonymous swrl var " + var);
        }
        return InternalObject.create(df.getSWRLVariable(IRI.create(var.getURI())), var);
    }

    /**
     * @param arg {@link OntSWRL.DArg}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link SWRLDArgument}
     */
    public static InternalObject<? extends SWRLDArgument> getSWRLLiteralArg(OntSWRL.DArg arg, OWLDataFactory df) {
        if (OntApiException.notNull(arg, "Null SWRL-D arg").isLiteral()) {
            return InternalObject.create(df.getSWRLLiteralArgument(getLiteral(arg.asLiteral(), df).getObject()), arg);
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return getSWRLVariable(arg.as(OntSWRL.Variable.class), df);
        }
        throw new OntApiException("Unsupported SWRL-D arg " + arg);
    }

    /**
     * @param arg {@link OntSWRL.IArg}
     * @param df  {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link SWRLIArgument}
     */
    public static InternalObject<? extends SWRLIArgument> getSWRLIndividualArg(OntSWRL.IArg arg, OWLDataFactory df) {
        if (OntApiException.notNull(arg, "Null SWRL-I arg").canAs(OntIndividual.class)) {
            return InternalObject.create(df.getSWRLIndividualArgument(fetchIndividual(arg.as(OntIndividual.class), df).getObject()), arg);
        }
        if (arg.canAs(OntSWRL.Variable.class)) {
            return getSWRLVariable(arg.as(OntSWRL.Variable.class), df);
        }
        throw new OntApiException("Unsupported SWRL-I arg " + arg);
    }

    /**
     * @param atom {@link OntSWRL.Atom}
     * @param df   {@link OWLDataFactory}
     * @return {@link InternalObject} around {@link SWRLAtom}
     */
    @SuppressWarnings("unchecked")
    public static InternalObject<? extends SWRLAtom> getSWRLAtom(OntSWRL.Atom atom, OWLDataFactory df) {
        Class<? extends OntObject> view = OntApiException.notNull(((OntObjectImpl) OntApiException.notNull(atom, "Null SWRL atom.")).getActualClass(),
                "Can't determine view of SWRL atom " + atom);
        if (OntSWRL.Atom.BuiltIn.class.equals(view)) {
            OntSWRL.Atom.BuiltIn _atom = (OntSWRL.Atom.BuiltIn) atom;
            IRI iri = IRI.create(_atom.getPredicate().getURI());
            List<InternalObject<? extends SWRLDArgument>> arguments = _atom.arguments().map(a -> getSWRLLiteralArg(a, df)).collect(Collectors.toList());
            SWRLAtom res = df.getSWRLBuiltInAtom(iri, arguments.stream().map(InternalObject::getObject).collect(Collectors.toList()));
            return InternalObject.create(res, _atom).appendWildcards(arguments);
        }
        if (OntSWRL.Atom.OntClass.class.equals(view)) {
            OntSWRL.Atom.OntClass _atom = (OntSWRL.Atom.OntClass) atom;
            InternalObject<? extends OWLClassExpression> c = getClassExpression(_atom.getPredicate(), df, new HashSet<>());
            InternalObject<? extends SWRLIArgument> a = getSWRLIndividualArg(_atom.getArg(), df);
            return InternalObject.create(df.getSWRLClassAtom(c.getObject(), a.getObject()), _atom).append(c).append(a);
        }
        if (OntSWRL.Atom.DataProperty.class.equals(view)) {
            OntSWRL.Atom.DataProperty _atom = (OntSWRL.Atom.DataProperty) atom;
            InternalObject<OWLDataProperty> p = fetchDataProperty(_atom.getPredicate(), df);
            InternalObject<? extends SWRLIArgument> f = getSWRLIndividualArg(_atom.getFirstArg(), df);
            InternalObject<? extends SWRLDArgument> s = getSWRLLiteralArg(_atom.getSecondArg(), df);
            return InternalObject.create(df.getSWRLDataPropertyAtom(p.getObject(), f.getObject(), s.getObject()), _atom).append(p).append(f).append(s);
        }
        if (OntSWRL.Atom.ObjectProperty.class.equals(view)) {
            OntSWRL.Atom.ObjectProperty _atom = (OntSWRL.Atom.ObjectProperty) atom;
            InternalObject<? extends OWLObjectPropertyExpression> p = fetchObjectPropertyExpression(_atom.getPredicate(), df);
            InternalObject<? extends SWRLIArgument> f = getSWRLIndividualArg(_atom.getFirstArg(), df);
            InternalObject<? extends SWRLIArgument> s = getSWRLIndividualArg(_atom.getSecondArg(), df);
            return InternalObject.create(df.getSWRLObjectPropertyAtom(p.getObject(), f.getObject(), s.getObject()), _atom).append(p).append(f).append(s);
        }
        if (OntSWRL.Atom.DataRange.class.equals(view)) {
            OntSWRL.Atom.DataRange _atom = (OntSWRL.Atom.DataRange) atom;
            InternalObject<? extends OWLDataRange> d = getDataRange(_atom.getPredicate(), df, new HashSet<>());
            InternalObject<? extends SWRLDArgument> a = getSWRLLiteralArg(_atom.getArg(), df);
            return InternalObject.create(df.getSWRLDataRangeAtom(d.getObject(), a.getObject()), _atom).append(d).append(a);
        }
        if (OntSWRL.Atom.DifferentIndividuals.class.equals(view)) {
            OntSWRL.Atom.DifferentIndividuals _atom = (OntSWRL.Atom.DifferentIndividuals) atom;
            InternalObject<? extends SWRLIArgument> f = getSWRLIndividualArg(_atom.getFirstArg(), df);
            InternalObject<? extends SWRLIArgument> s = getSWRLIndividualArg(_atom.getSecondArg(), df);
            return InternalObject.create(df.getSWRLDifferentIndividualsAtom(f.getObject(), s.getObject()), _atom).append(f).append(s);
        }
        if (OntSWRL.Atom.SameIndividuals.class.equals(view)) {
            OntSWRL.Atom.SameIndividuals _atom = (OntSWRL.Atom.SameIndividuals) atom;
            InternalObject<? extends SWRLIArgument> f = getSWRLIndividualArg(_atom.getFirstArg(), df);
            InternalObject<? extends SWRLIArgument> s = getSWRLIndividualArg(_atom.getSecondArg(), df);
            return InternalObject.create(df.getSWRLSameIndividualAtom(f.getObject(), s.getObject()), _atom).append(f).append(s);
        }
        throw new OntApiException("Unsupported SWRL atom " + atom);
    }

    /**
     * answers true if two nary axioms intersect, i.e. they have the same annotations and some components are included in both axioms.
     *
     * @param left  OWLNaryAxiom left axiom
     * @param right OWLNaryAxiom right axiom
     * @return true if axioms intersect.
     */
    public static boolean isIntersect(OWLNaryAxiom left, OWLNaryAxiom right) {
        if (!OWLAPIStreamUtils.equalStreams(left.annotations(), right.annotations())) return false;
        Set set1 = ((Stream<?>) left.operands()).collect(Collectors.toSet());
        Set set2 = ((Stream<?>) right.operands()).collect(Collectors.toSet());
        return !Collections.disjoint(set1, set2);
    }

}
