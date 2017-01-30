package ru.avicomp.ontapi.jena.utils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDFS;

import ru.avicomp.ontapi.jena.OntJenaException;
import ru.avicomp.ontapi.jena.vocabulary.OWL;
import ru.avicomp.ontapi.jena.vocabulary.RDF;

/**
 * Helper to test all kind of OWL Objects (OWL Entities, OWL Class&ObjectProperty Expressions, OWL DataRanges, OWL Anonymous Individuals)
 * inside the jena model {@link Model} according to <a href='https://www.w3.org/TR/owl2-quick-reference/'>OWL2 Specification</a>.
 * To use inside {@link ru.avicomp.ontapi.jena.converters.TransformAction} mostly.
 * <p>
 * Created by @szuev on 28.01.2017.
 */
public class OntRules {
    private static RuleEngine rules = new DefaultRuleEngine();

    public static RuleEngine getRules() {
        return rules;
    }

    public static void setRules(RuleEngine engine) {
        rules = OntJenaException.notNull(engine, "Null rule engine specified.");
    }

    public static boolean isAnnotationProperty(Model model, Resource candidate) {
        return rules.isAnnotationProperty(model, candidate);
    }

    public static boolean isDataProperty(Model model, Resource candidate) {
        return rules.isDataProperty(model, candidate);
    }

    public static boolean isObjectPropertyExpression(Model model, Resource candidate) {
        return rules.isObjectPropertyExpression(model, candidate);
    }

    public static boolean isDataRange(Model model, Resource candidate) {
        return rules.isDataRange(model, candidate);
    }

    public static boolean isClassExpression(Model model, Resource candidate) {
        return rules.isClassExpression(model, candidate);
    }

    public static boolean isIndividual(Model model, Resource candidate) {
        return rules.isIndividual(model, candidate);
    }

    public interface RuleEngine {
        boolean isAnnotationProperty(Model model, Resource candidate);

        boolean isDataProperty(Model model, Resource candidate);

        boolean isObjectPropertyExpression(Model model, Resource candidate);

        boolean isDataRange(Model model, Resource candidate);

        boolean isClassExpression(Model model, Resource candidate);

        boolean isIndividual(Model model, Resource candidate);

        default boolean isObjectProperty(Model model, Resource candidate) {
            return candidate.isURIResource() && isObjectPropertyExpression(model, candidate);
        }

        default boolean isClass(Model model, Resource candidate) {
            return candidate.isURIResource() && isClassExpression(model, candidate);
        }

        default boolean isDatatype(Model model, Resource candidate) {
            return candidate.isURIResource() && isDataRange(model, candidate);
        }
    }

    /**
     * The default Rule Engine Implementation.
     * <p>
     * Should work correctly and safe with any ontology (OWL-1, RDFS) using the "strict" approach:
     * if some resource has no clear hints about the type in the specified model than the corresponding method should return 'false'.
     * In other words the conditions to test should be necessary and sufficient.
     * This is in order not to produce unnecessary punnings.
     * But please NOTE: the punnings is not taken into account in obvious cases.
     * E.g. if the property has rdfs:range with rdf:type = rdfs:Datatype assigned then it is a Data Property,
     * even some other tips show it is Annotation Property or something else.
     */
    public static class DefaultRuleEngine implements RuleEngine {
        private static final String DATA_PROPERTY_KEY = "DataProperty";
        private static final String OBJECT_PROPERTY_KEY = "ObjectProperty";
        private static final String ANNOTATION_PROPERTY_KEY = "AnnotationProperty";
        private static final String CLASS_EXPRESSION_KEY = "ClassExpression";
        private static final String DATA_RANGE_KEY = "DataRange";
        private static final String INDIVIDUAL_KEY = "Individual";

        /**
         * checks that specified uri-resource(rdf:Property) is a Annotation Property (owl:AnnotationProperty).
         * It's marked as 'A' in the <a href='https://www.w3.org/TR/owl2-quick-reference/'>reminder</a>.
         * <p>
         * Note: we don't check rdfs:domain and rdfs:range statements since theirs right part is just an uri,
         * so could be treated as standalone resource or be defined in the graph as class expressions or as anything else.
         * Checking of non-existence such constructions seems contrary to the strict approach
         * because the model can be used in imports of some other ontology where that uri has explanation.
         * It seems there are no way to determine exactly("necessary and sufficient") that the candidate is an annotation property
         * if there are only domain or range axioms.
         * Also we skip the check for annotation property assertions ("s A t") since it can be confused with data property assertion ("a R v").
         * <p>
         * There are following conditions to test:
         * 0) it is an uri-resource
         * 1) it's in builtin list (rdfs:label, owl:deprecated ... etc)
         * 2) it has the an explicit type owl:AnnotationProperty (or owl:OntologyProperty for annotations in owl:Ontology section, deprecated)
         * 3) any non-builtin predicate from anon section with type owl:Axiom, owl:Annotation (bulk annotations)
         * 4) recursive object's checking for triple "A1 rdfs:subPropertyOf A2"
         * 5) recursive subject's checking for triple "A1 rdfs:subPropertyOf A2"
         *
         * @param model     {@link Model}
         * @param candidate the {@link Resource} to test.
         * @return true if this uri-resource is an Annotation Property (owl:AnnotationProperty)
         */
        @Override
        public boolean isAnnotationProperty(Model model, Resource candidate) {
            return isAnnotationProperty(model, candidate, new HashMap<>());
        }

        /**
         * checks that specified resource(rdf:Property) is a Data Property (owl:DatatypeProperty).
         * It's marked as 'R' in the <a href='https://www.w3.org/TR/owl2-quick-reference/'>reminder</a>.
         * <p>
         * Note: the checking for "R rdfs:domain C" and "a R v" are skipped now, because they could be confused with similar axioms
         * but for an annotation property ("s A t" and "A rdfs:domain U").
         * Here: 'v' is a literal, 'a' is an individual, 's' is IRI or anonymous individual, 'U' is IRI.
         * <p>
         * There are following conditions to test (the sequence are important because it depends on other recursive methods):
         * 0) it is an uri-resource
         * 1) it's in builtin list (owl:topDataProperty, owl:bottomDataProperty).
         * 2) it has the an explicit type owl:DatatypeProperty
         * 3) it contains in negative data property assertion (owl:NegativePropertyAssertion, checks for owl:targetValue).
         * 4) it is in the list of properties (predicate owl:onProperties) from N-ary Data Range Restriction.
         * 5) it is attached on predicate owl:onProperty to some qualified cardinality restriction (checking for owl:onDataRange predicate)
         * 6) it is part of owl:Restriction with literal on predicate owl:hasValue.
         * 7) recursively checks the objects from the triples with predicate owl:equivalentProperty, owl:propertyDisjointWith, rdfs:subPropertyOf
         * 8) recursively checks the subjects for the same statements (the candidate resource is regarded now as an object in a triple)
         * 9) recursively checks the objects from owl:AllDisjointProperties(owl:members) anonymous section.
         * 10) it has a data range (Datatype(DN) or DataRange(D)): "R rdfs:range D"
         * 11) it is a property in the universal and existential data property restrictions (checking for owl:someValuesFrom or owl:allValuesFrom = D).
         *
         * @param model     model {@link Model}
         * @param candidate {@link Resource} to test.
         * @return true if this uri-resource is a Data Property
         */
        @Override
        public boolean isDataProperty(Model model, Resource candidate) {
            return isDataProperty(model, candidate, new HashMap<>());
        }

        /**
         * checks that specified resource is an Object Property Expression.
         * The object property is marked 'PN' in the <a href='https://www.w3.org/TR/owl2-quick-reference/'>reminder</a>,
         * while general object property expression holds the designation of 'P'.
         * <p>
         * There are following conditions to test:
         * 1) it's from builtin list (owl:topObjectProperty, owl:bottomObjectProperty).
         * 2) it has the an explicit type owl:ObjectProperty
         * 3) it has one of the following types:
         * - owl:InverseFunctionalProperty,
         * - owl:ReflexiveProperty,
         * - owl:IrreflexiveProperty,
         * - owl:SymmetricProperty,
         * - owl:AsymmetricProperty,
         * - owl:TransitiveProperty
         * 4) it is left or right part of "P1 owl:inverseOf P2"
         * 5) part of "P owl:propertyChainAxiom (P1 ... Pn)"
         * 6) attached to a negative object property assertion (owl:NegativePropertyAssertion, the check for owl:targetIndividual).
         * 7) it is a subject in "_:x owl:hasSelf "true"^^xsd:boolean" (local reflexivity object property restriction)
         * 8) it is attached on predicate owl:onProperty to some qualified cardinality object property restriction (checking for owl:onClass predicate)
         * 9) it is part of owl:Restriction with non-literal on predicate owl:hasValue (it has to be an individual).
         * 10) recursively checks objects from triples with predicate owl:equivalentProperty, owl:propertyDisjointWith, rdfs:subPropertyOf
         * 11) recursively checks subjects for the same statements (the input resource is regarded as an object in the triple)
         * 12) recursively checks objects from owl:AllDisjointProperties(owl:members)
         * 13) the range is a class expression: "P rdfs:range C".
         * 14) it is a property in the universal and existential object property restrictions (checking for owl:someValuesFrom or owl:allValuesFrom = C).
         * 15) a predicate in positive object property assertion "a1 PN a2".
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @return true if this resource is an Object Property
         */
        @Override
        public boolean isObjectPropertyExpression(Model model, Resource candidate) {
            return isObjectPropertyExpression(model, candidate, new HashMap<>());
        }

        /**
         * checks that specified resource(rdfs:Class) is a Data Range Expression or a Datatype
         * ('D' and 'DN' in in the <a href='https://www.w3.org/TR/owl2-quick-reference/'>reminder</a>).
         * <p>
         * There are following conditions to test:
         * 1) it's from builtin list (rdfs:Literal, xsd:string, owl:rational ... etc).
         * 2) it has the an explicit type rdfs:Datatype or owl:DataRange(deprecated in OWL 2)
         * 3) it is a subject or an object in the statement "_:x owl:datatypeComplementOf D" (data range complement)
         * 4) it is a subject or an uri-object in the statement "_:x owl:onDatatype DN; _:x owl:withRestrictions (...)" (datatype restriction)
         * 5) it is a subject in the statement which contains a list of literals as an object "_:x owl:oneOf (v1 ... vn)"
         * 6) it is the right part of owl:onDataRange inside "_:x rdf:type owl:Restriction" (data property qualified cardinality restriction)
         * 7) it is an object with predicate owl:someValuesFrom or owl:allValuesFrom inside N-ary Data Property Restrictions
         * Recursive checks:
         * 8) it is left(uri) or right part of statement "DN owl:equivalentClass D" which contains another data range.
         * 9.1) recursively checks objects inside lists with predicates owl:intersectionOf and owl:unionOf.
         * 9.2) the same but for a subject. if tested resource is in the list which corresponds data range union or data range intersection.
         * 10) it is attached on predicate owl:someValuesFrom or owl:allValuesFrom where onProperty is R
         * 11) it is a range for data property: "R rdfs:range D"
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @return true if this resource is an Object Property
         */
        @Override
        public boolean isDataRange(Model model, Resource candidate) {
            return isDataRange(model, candidate, new HashMap<>());
        }

        /**
         * checks that specified resource(rdfs:Class) is a Class Expression.
         * ('C' and 'CN' in in the <a href='https://www.w3.org/TR/owl2-quick-reference/'>reminder</a>).
         * <p>
         * There are following conditions to test on Class Expression:
         * 1) it's from builtin list (owl:Nothing, owl:Thing).
         * 2) it has the an explicit type owl:Class or owl:Restriction(for anonymous CE)
         * 3) it is the right part of statement with predicate owl:hasKey ("C owl:hasKey (P1 ... Pm R1 ... Rn)")
         * 4) left or right part of "C1 rdfs:subClassOf C2"
         * 5) left or right part of "C1 owl:disjointWith C2"
         * 6) a subject (anonymous in OWL2) or an object in a statement with predicate owl:complementOf (Object complement of CE)
         * 7) left or right part of "CN owl:disjointUnionOf (C1 ... Cn)"
         * 8) an object in a statement with predicate owl:onClass (cardinality restriction)
         * 9) member in the list with predicate owl:members and rdf:type = owl:AllDisjointClasses
         * 10) it is a subject in the statement which contains a list of individuals as an object "_:x owl:oneOf (a1 ... an)"
         * 11) it could be owl:Restriction but with missed rdf:type
         * - local reflexivity object property restriction
         * - universal/existential data/object property restriction
         * - individual/literal value object/data property restriction
         * - maximum/minimum/exact cardinality object/data property restriction
         * - maximum/minimum/exact qualified cardinality object/data property restriction
         * - n-ary universal/existential data property restriction
         * 12) recursively for owl:equivalentClass (the right and left parts should have the same type)
         * 13) recursively for owl:intersectionOf, owl:unionOf (analogically for data ranges)
         * 14) it is attached on predicate owl:someValuesFrom or owl:allValuesFrom where onProperty is P
         * 15) if subject of range statement is an object property ("P rdfs:range C")
         * 16) if subject of domain statement is an object or a data property ("P rdfs:domain C" and "R rdfs:domain C")
         * 17) class assertion ("a rdf:type C")
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @return true if this resource is a Class Expression
         */
        @Override
        public boolean isClassExpression(Model model, Resource candidate) {
            return isClassExpression(model, candidate, new HashMap<>());
        }

        /**
         * checks that resource is an anonymous or named individual.
         * see also {@link ru.avicomp.ontapi.jena.model.OntIndividual.Anonymous} description.
         * <p>
         * There are following conditions to test on Individual:
         * 1) it is an uri-resource and has the an explicit type owl:NamedIndividual
         * 2) it is a subject or an object in a statement with predicate owl:sameAs or owl:differentFrom
         * 3) it is contained in a owl:AllDifferent (rdf:List with predicate owl:distinctMembers or owl:members)
         * 4) it is contained in a rdf:List with predicate owl:oneOf
         * 5) it is a part of owl:NegativePropertyAssertion section with predicates owl:sourceIndividual or owl:targetIndividual
         * 6) object property restriction "_:x owl:hasValue a."
         * 7) class assertion (declaration) "_:a rdf:type C"
         * 8) positive data property assertion "a R v" or annotation assertion "a A v"
         * 9) positive object property assertion "a1 PN a2" or annotation assertion "a1 A a2"
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @return true if this resource is an Individual
         */
        @Override
        public boolean isIndividual(Model model, Resource candidate) {
            return isIndividual(model, candidate, new HashMap<>());
        }

        /**
         * description see here {@link #isAnnotationProperty(Model, Resource)}
         *
         * @param model     {@link Model}
         * @param candidate the {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true if this uri-resource is an Annotation Property (owl:AnnotationProperty)
         */
        private static boolean isAnnotationProperty(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(ANNOTATION_PROPERTY_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            // 0) annotation property is always IRI
            if (!candidate.isURIResource()) {
                return false;
            }
            // 1) builtin
            if (BuiltIn.ANNOTATION_PROPERTIES.contains(candidate)) {
                return true;
            }
            // 2) an explicit type
            if (model.contains(candidate, RDF.type, OWL.AnnotationProperty) || model.contains(candidate, RDF.type, OWL.OntologyProperty)) {
                return true;
            }
            // 3) annotations assertions inside bulk annotation object:
            Property property = candidate.as(Property.class);
            if (subjects(model, property)
                    .filter(RDFNode::isAnon)
                    .filter(r -> r.hasProperty(RDF.type, OWL.Annotation) || r.hasProperty(RDF.type, OWL.Axiom))
                    .filter(r -> r.hasProperty(OWL.annotatedProperty))
                    .filter(r -> r.hasProperty(OWL.annotatedSource))
                    .anyMatch(r -> r.hasProperty(OWL.annotatedTarget))) {
                return true;
            }
            // 4-5) recursion:
            Stream<Resource> objects = objects(model, candidate, RDFS.subPropertyOf);
            Stream<Resource> subjects = subjects(model, RDFS.subPropertyOf, candidate);
            Stream<Resource> test = Stream.concat(objects, subjects);
            return test
                    .filter(p -> !processed.contains(p))
                    .anyMatch(p -> isAnnotationProperty(model, p, seen));
        }

        /**
         * full description see here {@link #isDataProperty(Model, Resource)}
         *
         * @param model     {@link Model}
         * @param candidate the {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true if this uri-resource is a Data Property (owl:DatatypeProperty)
         */
        private static boolean isDataProperty(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(DATA_PROPERTY_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            // 0) data property expression is always named
            if (!candidate.isURIResource()) {
                return false;
            }
            // 1) builtin
            if (BuiltIn.DATA_PROPERTIES.contains(candidate)) {
                return true;
            }
            // 2) has an explicit type
            if (objects(model, candidate, RDF.type).anyMatch(OWL.DatatypeProperty::equals)) {
                return true;
            }
            // 3) negative assertion:
            if (subjects(model, OWL.assertionProperty, candidate)
                    .filter(r -> r.hasProperty(RDF.type, OWL.NegativePropertyAssertion))
                    .filter(r -> r.hasProperty(OWL.sourceIndividual))
                    .filter(r -> r.hasProperty(OWL.targetValue))
                    .map(r -> r.getProperty(OWL.targetValue)).map(Statement::getObject).anyMatch(RDFNode::isLiteral)) {
                return true;
            }
            // 4) properties from nary restriction:
            if (isInList(model, candidate) && naryDataPropertyRestrictions(model).anyMatch(l -> l.contains(candidate))) {
                return true;
            }
            // 5) qualified maximum/minimum/exact cardinality data property restrictions
            // 6) literal value data property restriction
            if (subjects(model, OWL.onProperty, candidate)
                    .filter(DefaultRuleEngine::isRestriction)
                    .anyMatch(r -> r.hasProperty(OWL.onDataRange) ||
                            (r.hasProperty(OWL.hasValue) && r.getProperty(OWL.hasValue).getObject().isLiteral()))) {
                return true;
            }

            // 7-9) recursive checks:
            // 7) collect objects (resources from the right side):
            Stream<Resource> objects = Stream.of(RDFS.subPropertyOf, OWL.equivalentProperty, OWL.propertyDisjointWith)
                    .map(p -> objects(model, candidate, p)).flatMap(Function.identity());
            // 8) collect subjects
            Stream<Resource> subjects = Stream.of(RDFS.subPropertyOf, OWL.equivalentProperty, OWL.propertyDisjointWith)
                    .map(p -> subjects(model, p, candidate)).flatMap(Function.identity());
            // 9) collect disjoint properties
            Stream<Resource> disjoint = allDisjointPropertiesByMember(model, candidate);
            Stream<Resource> test = Stream.of(objects, subjects, disjoint).flatMap(Function.identity());
            if (test.filter(p -> !processed.contains(p)).anyMatch(p -> isDataProperty(model, p, seen))) {
                return true;
            }

            // 10) rdfs:range is D
            if (objects(model, candidate, RDFS.range).anyMatch(r -> isDataRange(model, r, seen))) {
                return true;
            }
            // 11) owl:someValuesFrom or owl:allValuesFrom are D:
            if (subjects(model, OWL.onProperty, candidate)
                    .filter(DefaultRuleEngine::isRestriction)
                    .map(r -> r.hasProperty(OWL.someValuesFrom) ? objects(model, r, OWL.someValuesFrom) : objects(model, r, OWL.allValuesFrom))
                    .flatMap(Function.identity())
                    .anyMatch(r -> isDataRange(model, r, seen))) {
                return true;
            }
            // then it is not R
            return false;
        }

        /**
         * description see here {@link #isObjectProperty(Model, Resource)}
         *
         * @param model     {@link Model}
         * @param candidate the {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true if this uri-resource is an Object Property (owl:ObjectProperty)
         */
        private static boolean isObjectPropertyExpression(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(OBJECT_PROPERTY_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            // 1) builtin
            if (BuiltIn.OBJECT_PROPERTIES.contains(candidate)) {
                return true;
            }
            // 2) an explicit type
            if (objects(model, candidate, RDF.type).anyMatch(OWL.ObjectProperty::equals)) {
                return true;
            }
            // 3) always Object Property
            if (Stream.of(OWL.InverseFunctionalProperty, OWL.TransitiveProperty, OWL.SymmetricProperty, OWL.AsymmetricProperty,
                    OWL.ReflexiveProperty, OWL.IrreflexiveProperty).anyMatch(r -> model.contains(candidate, RDF.type, r))) {
                return true;
            }
            // 4) any part of owl:inverseOf
            if (model.contains(candidate, OWL.inverseOf) || model.contains(null, OWL.inverseOf, candidate)) {
                return true;
            }
            // 5) part of owl:propertyChainAxiom
            if (model.contains(candidate, OWL.propertyChainAxiom) || containsInList(model, OWL.propertyChainAxiom, candidate)) {
                return true;
            }
            // 6) negative object property assertion:
            if (subjects(model, OWL.assertionProperty, candidate)
                    .filter(r -> r.hasProperty(RDF.type, OWL.NegativePropertyAssertion))
                    .filter(r -> r.hasProperty(OWL.sourceIndividual))
                    .filter(r -> r.hasProperty(OWL.targetIndividual))
                    .map(r -> r.getProperty(OWL.targetIndividual)).map(Statement::getObject).anyMatch(RDFNode::isResource)) {
                return true;
            }
            // 7) it is a subject in "_:x owl:hasSelf "true"^^xsd:boolean" (local reflexivity object property restriction)
            // 8) it is attached on predicate owl:onProperty to some qualified cardinality object property restriction (checking for owl:onClass predicate)
            // 9) it is part of owl:Restriction with non-literal on predicate owl:hasValue (it has to be an individual).
            if (subjects(model, OWL.onProperty, candidate)
                    .filter(DefaultRuleEngine::isRestriction)
                    .anyMatch(r -> r.hasProperty(OWL.onClass) ||
                            r.hasProperty(OWL.hasSelf, Models.TRUE) ||
                            (r.hasProperty(OWL.hasValue) && r.getProperty(OWL.hasValue).getObject().isResource()))) {
                return true;
            }
            // 10-12) recursions:
            Stream<Resource> objects = Stream.of(RDFS.subPropertyOf, OWL.equivalentProperty, OWL.propertyDisjointWith)
                    .map(p -> objects(model, candidate, p)).flatMap(Function.identity());
            Stream<Resource> subjects = Stream.of(RDFS.subPropertyOf, OWL.equivalentProperty, OWL.propertyDisjointWith)
                    .map(p -> subjects(model, p, candidate)).flatMap(Function.identity());
            Stream<Resource> disjoint = allDisjointPropertiesByMember(model, candidate);
            Stream<Resource> test = Stream.of(objects, subjects, disjoint).flatMap(Function.identity());
            if (test.filter(p -> !processed.contains(p)).anyMatch(p -> isObjectPropertyExpression(model, p, seen))) {
                return true;
            }
            // 13) rdfs:range is C
            if (objects(model, candidate, RDFS.range).anyMatch(r -> isClassExpression(model, r, seen))) {
                return true;
            }
            // 14) owl:someValuesFrom or owl:allValuesFrom = C.
            if (subjects(model, OWL.onProperty, candidate)
                    .filter(DefaultRuleEngine::isRestriction)
                    .map(r -> r.hasProperty(OWL.someValuesFrom) ? objects(model, r, OWL.someValuesFrom) : objects(model, r, OWL.allValuesFrom))
                    .flatMap(Function.identity())
                    .anyMatch(r -> isClassExpression(model, r, seen))) {
                return true;
            }
            // 15) positive object property assertion a1 PN a2.
            if (candidate.isURIResource() && statements(model, null, candidate.as(Property.class), null)
                    .filter(s -> isIndividual(model, s.getSubject(), seen))
                    .map(Statement::getObject)
                    .filter(RDFNode::isResource)
                    .map(RDFNode::asResource).anyMatch(r -> isIndividual(model, r, seen))) {
                return true;
            }
            // not an object property
            return false;
        }

        /**
         * see description {@link #isDataRange(Model, Resource)}
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true if this resource is a DataRange expression or a Datatype (_:x rdf:type rdfs:Datatype)
         */
        private static boolean isDataRange(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(DATA_RANGE_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            // 1) builtin
            if (BuiltIn.DATATYPES.contains(candidate)) {
                return true;
            }
            // 2) type
            if (model.contains(candidate, RDF.type, RDFS.Datatype) || model.contains(candidate, RDF.type, OWL.DataRange)) {
                return true;
            }
            // 3) data range complement expression
            if (model.contains(candidate, OWL.datatypeComplementOf) || model.contains(null, OWL.datatypeComplementOf, candidate)) {
                return true;
            }
            // 4) Datatype restriction
            if (subjects(model, OWL.onDatatype, candidate)
                    .map(r -> objects(model, r, OWL.withRestrictions))
                    .flatMap(Function.identity())
                    .anyMatch(r -> r.canAs(RDFList.class))) {
                return true;
            }
            if (model.contains(candidate, OWL.onDatatype) && objects(model, candidate, OWL.withRestrictions).anyMatch(r -> r.canAs(RDFList.class))) {
                return true;
            }
            // 5) literal enumeration data range expression:
            if (objects(model, candidate, OWL.oneOf)
                    .filter(r -> r.canAs(RDFList.class))
                    .map(r -> r.as(RDFList.class))
                    .map(RDFList::asJavaList)
                    .anyMatch(l -> !l.isEmpty() && l.stream().allMatch(RDFNode::isLiteral))) {
                return true;
            }
            // 6) data property qualified cardinality restriction
            if (subjects(model, OWL.onDataRange, candidate).anyMatch(DefaultRuleEngine::isRestriction)) {
                return true;
            }
            // 7) n-ary data property restrictions
            if (multiSubjects(model, candidate, OWL.someValuesFrom, OWL.allValuesFrom)
                    .map(r -> objects(model, r, OWL.onProperties)).flatMap(Function.identity())
                    .anyMatch(r -> r.canAs(RDFList.class))) {
                return true;
            }

            // recursions:
            // 8) "DN owl:equivalentClass D"
            Stream<Resource> equivalentObjects = candidate.isURIResource() ? objects(model, candidate, OWL.equivalentClass) : Stream.empty();
            Stream<Resource> equivalentSubjects = subjects(model, OWL.equivalentClass, candidate);
            // 9) "_:x owl:unionOf (D1...Dn)" and "_:x owl:intersectionOf (D1...Dn)"
            Stream<Resource> expressionObjects = objectsFromLists(model, candidate, OWL.unionOf, OWL.intersectionOf);
            Stream<Resource> expressionSubjects = subjectsByListMember(model, candidate, OWL.unionOf, OWL.intersectionOf);
            Stream<Resource> test = Stream.of(equivalentObjects, equivalentSubjects, expressionObjects, expressionSubjects).flatMap(Function.identity());
            if (test.filter(p -> !processed.contains(p)).anyMatch(p -> isDataRange(model, p, seen))) {
                return true;
            }
            // 10) universal and existential data property restrictions
            if (multiSubjects(model, candidate, OWL.someValuesFrom, OWL.allValuesFrom)
                    .filter(r -> r.hasProperty(OWL.onProperty))
                    .map(r -> r.getProperty(OWL.onProperty)).map(Statement::getObject)
                    .filter(RDFNode::isResource).map(RDFNode::asResource)
                    .anyMatch(r -> isDataProperty(model, r, seen))) {
                return true;
            }
            // 11) "R rdfs:range D"
            if (subjects(model, RDFS.range, candidate).anyMatch(r -> isDataProperty(model, r, seen))) {
                return true;
            }
            // not a Data Range
            return false;
        }

        /**
         * see description {@link #isClassExpression(Model, Resource)}
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true it is a Class Expression
         */
        private static boolean isClassExpression(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(CLASS_EXPRESSION_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            if (BuiltIn.CLASSES.contains(candidate)) {
                return true;
            }
            if (model.contains(candidate, RDF.type, OWL.Class) || isRestriction(candidate.inModel(model))) {
                return true;
            }
            // 3) "C owl:hasKey (P1 ... Pm R1 ... Rn)"
            if (objects(model, candidate, OWL.hasKey).anyMatch(r -> r.canAs(RDFList.class))) {
                return true;
            }
            // 4-6) "C1 rdfs:subClassOf C2", "C1 owl:disjointWith C2", "_:x rdf:type owl:Class; _:x owl:complementOf C"
            if (Stream.of(RDFS.subClassOf, OWL.disjointWith, OWL.complementOf)
                    .map(p -> Stream.concat(subjects(model, p, candidate), objects(model, candidate, p)))
                    .flatMap(Function.identity())
                    .anyMatch(r -> true)) {
                return true;
            }
            // 7) "CN owl:disjointUnionOf (C1 ... Cn)"
            if ((candidate.isURIResource() && model.contains(candidate, OWL.disjointUnionOf)) || containsInList(model, OWL.disjointUnionOf, candidate)) {
                return true;
            }
            // 8) object property qualified cardinality restriction
            if (subjects(model, OWL.onClass, candidate).anyMatch(DefaultRuleEngine::isRestriction)) {
                return true;
            }
            // 9) member in the list with predicate owl:members and rdf:type = owl:AllDisjointClasses
            if (isInList(model, candidate) && allDisjointClasses(model).anyMatch(list -> list.contains(candidate))) {
                return true;
            }
            // 10) individuals enumeration:
            if (objects(model, candidate, OWL.oneOf)
                    .filter(r -> r.canAs(RDFList.class))
                    .map(r -> r.as(RDFList.class))
                    .map(RDFList::asJavaList)
                    .anyMatch(l -> !l.isEmpty() && l.stream().allMatch(RDFNode::isResource))) {
                return true;
            }
            // 11) test parts of owl:Restriction.
            if (objects(model, candidate, OWL.onProperty).anyMatch(RDFNode::isResource)) {
                if (model.contains(candidate, OWL.hasSelf, Models.TRUE)) {
                    return true;
                }
                if (multiObjects(model, candidate, OWL.someValuesFrom, OWL.allValuesFrom)
                        .anyMatch(RDFNode::isResource)) {
                    return true;
                }
                if (model.contains(candidate, OWL.hasValue)) {
                    return true;
                }
                if (Stream.of(OWL.cardinality, OWL.maxCardinality, OWL.minCardinality)
                        .map(p -> objects(model, candidate, p)).flatMap(Function.identity())
                        .anyMatch(RDFNode::isLiteral)) {
                    return true;
                }
                if (Stream.of(OWL.qualifiedCardinality, OWL.maxQualifiedCardinality, OWL.minQualifiedCardinality)
                        .map(p -> objects(model, candidate, p)).flatMap(Function.identity()).anyMatch(RDFNode::isLiteral) &&
                        Stream.of(OWL.onClass, OWL.onDataRange)
                                .map(p -> objects(model, candidate, p)).flatMap(Function.identity()).anyMatch(r -> true)) {
                    return true;
                }
            }
            if (objects(model, candidate, OWL.onProperties).anyMatch(r -> r.canAs(RDFList.class)) &&
                    multiObjects(model, candidate, OWL.someValuesFrom, OWL.allValuesFrom).anyMatch(RDFNode::isResource)) {
                return true;
            }
            // recursion checks:
            // 12) "Cj owl:equivalentClass Cj+1"
            Stream<Resource> equivalentObjects = objects(model, candidate, OWL.equivalentClass);
            Stream<Resource> equivalentSubjects = subjects(model, OWL.equivalentClass, candidate);
            // 13) "_:x owl:intersectionOf (C1 ... Cn)" & "_:x owl:unionOf (C1 ... Cn)"
            Stream<Resource> expressionObjects = objectsFromLists(model, candidate, OWL.unionOf, OWL.intersectionOf);
            Stream<Resource> expressionSubjects = subjectsByListMember(model, candidate, OWL.unionOf, OWL.intersectionOf);
            Stream<Resource> test = Stream.of(equivalentObjects, equivalentSubjects, expressionObjects, expressionSubjects).flatMap(Function.identity());
            if (test.filter(p -> !processed.contains(p)).anyMatch(p -> isClassExpression(model, p, seen))) {
                return true;
            }
            // 14) universal and existential object property restrictions
            if (multiSubjects(model, candidate, OWL.someValuesFrom, OWL.allValuesFrom)
                    .filter(r -> r.hasProperty(OWL.onProperty))
                    .map(r -> r.getProperty(OWL.onProperty)).map(Statement::getObject)
                    .filter(RDFNode::isResource).map(RDFNode::asResource)
                    .anyMatch(r -> isObjectPropertyExpression(model, r, seen))) {
                return true;
            }
            // 15) "P rdfs:range C"
            if (subjects(model, RDFS.range, candidate)
                    .anyMatch(r -> isObjectPropertyExpression(model, r, seen))) {
                return true;
            }
            // 16) "P rdfs:domain C" or "R rdfs:domain C"
            if (subjects(model, RDFS.domain, candidate)
                    .anyMatch(r -> isDataProperty(model, r, seen) || isObjectPropertyExpression(model, r, seen))) {
                return true;
            }
            // 17) "a rdf:type C"
            if (subjects(model, RDF.type, candidate)
                    .anyMatch(r -> isIndividual(model, r, seen))) {
                return true;
            }
            // not a CE:
            return false;
        }

        /**
         * see description {@link #isIndividual(Model, Resource)}
         * <p>
         *
         * @param model     {@link Model}
         * @param candidate {@link Resource} to test.
         * @param seen      the map to avoid recursion
         * @return true it is a named or anonymous individual
         */
        private static boolean isIndividual(Model model, Resource candidate, Map<String, Set<Resource>> seen) {
            Set<Resource> processed = seen.computeIfAbsent(INDIVIDUAL_KEY, c -> new HashSet<>());
            if (processed.contains(candidate)) return false;
            processed.add(candidate);
            if (candidate.isURIResource() && model.contains(candidate, RDF.type, OWL.NamedIndividual)) {
                return true;
            }
            // 2) "aj owl:sameAs aj+1", "a1 owl:differentFrom a2"
            if (Stream.of(OWL.sameAs, OWL.differentFrom).anyMatch(p -> model.contains(candidate, p) || model.contains(null, p, candidate))) {
                return true;
            }
            // 3) owl:AllDifferent
            if (isInList(model, candidate) && allDifferent(model).anyMatch(list -> list.contains(candidate))) {
                return true;
            }
            // 4) owl:oneOf
            if (containsInList(model, OWL.oneOf, candidate)) {
                return true;
            }
            // 5) owl:NegativePropertyAssertion
            if (multiSubjects(model, candidate, OWL.sourceIndividual, OWL.targetIndividual)
                    .filter(r -> r.hasProperty(OWL.assertionProperty))
                    .anyMatch(r -> r.hasProperty(RDF.type, OWL.NegativePropertyAssertion))) {
                return true;
            }
            // 6) object property restriction "_:x owl:hasValue a."
            if (subjects(model, OWL.hasValue, candidate)
                    .filter(DefaultRuleEngine::isRestriction)
                    .anyMatch(r -> r.hasProperty(OWL.onProperty))) {
                return true;
            }
            // 7) class assertion (declaration) "_:a rdf:type C"
            if (objects(model, candidate, RDF.type).anyMatch(r -> isClassExpression(model, r, seen))) {
                return true;
            }
            // 8) positive data property assertion "a R v" or annotation assertion "a A v"
            if (statements(model, candidate, null, null)
                    .filter(s -> s.getObject().isLiteral())
                    .map(Statement::getPredicate)
                    .anyMatch(p -> isDataProperty(model, p, seen) || isAnnotationProperty(model, p, seen))) {
                return true;
            }
            // 9) positive object property assertion "a1 PN a2" or annotation assertion "a1 A a2"
            Stream<Resource> left = statements(model, null, null, candidate)
                    .filter(s -> isObjectPropertyExpression(model, s.getPredicate(), seen) || isAnnotationProperty(model, s.getPredicate(), seen))
                    .map(Statement::getSubject);
            Stream<Resource> right = statements(model, candidate, null, null)
                    .filter(s -> s.getObject().isResource())
                    .filter(s -> isObjectPropertyExpression(model, s.getPredicate(), seen) || isAnnotationProperty(model, s.getPredicate(), seen))
                    .map(Statement::getObject).map(RDFNode::asResource);
            Stream<Resource> test = Stream.concat(left, right);
            if (test.filter(p -> !processed.contains(p)).anyMatch(p -> isIndividual(model, p, seen))) {
                return true;
            }
            // Not an individual:
            return false;
        }

        private static boolean isRestriction(Resource root) {
            return root.isAnon() && root.hasProperty(RDF.type, OWL.Restriction);
        }

        private static Stream<Resource> objectsFromLists(Model model, Resource subject, Property... predicates) {
            return Stream.of(predicates).map(p -> objects(model, subject, p)).flatMap(Function.identity())
                    .filter(r -> r.canAs(RDFList.class)).map(r -> r.as(RDFList.class))
                    .map(RDFList::asJavaList).map(Collection::stream).flatMap(Function.identity())
                    .filter(RDFNode::isResource).map(RDFNode::asResource);
        }

        private static Stream<Resource> subjectsByListMember(Model model, Resource member, Property... predicates) {
            return isInList(model, member) ?
                    Stream.of(predicates).map(p -> statements(model, null, p, null)).flatMap(Function.identity())
                            .filter(s -> s.getObject().canAs(RDFList.class))
                            .filter(s -> s.getObject().as(RDFList.class).asJavaList().contains(member))
                            .map(Statement::getSubject) : Stream.empty();
        }

        private static boolean isInList(Model model, Resource member) {
            return model.contains(null, RDF.first, member);
        }

        private static boolean containsInList(Model model, Property predicate, Resource member) {
            return isInList(model, member) && statements(model, null, predicate, null)
                    .map(Statement::getObject)
                    .filter(n -> n.canAs(RDFList.class))
                    .map(n -> n.as(RDFList.class))
                    .map(RDFList::asJavaList).anyMatch(l -> l.contains(member));
        }

        private static Stream<Resource> allDisjointPropertiesByMember(Model model, Resource member) {
            return isInList(model, member) ?
                    allDisjointProperties(model)
                            .filter(list -> list.contains(member))
                            .map(Collection::stream)
                            .flatMap(Function.identity())
                            .filter(RDFNode::isResource)
                            .map(RDFNode::asResource)
                    : Stream.empty();
        }

        private static Stream<List<RDFNode>> allDisjointProperties(Model model) {
            return lists(model, OWL.AllDisjointProperties, OWL.members);
        }

        private static Stream<List<RDFNode>> allDisjointClasses(Model model) {
            return lists(model, OWL.AllDisjointClasses, OWL.members);
        }

        private static Stream<List<RDFNode>> allDifferent(Model model) {
            return Stream.concat(lists(model, OWL.AllDifferent, OWL.members), lists(model, OWL.AllDifferent, OWL.distinctMembers));
        }

        private static Stream<List<RDFNode>> naryDataPropertyRestrictions(Model model) {
            return lists(model, OWL.Restriction, OWL.onProperties);
        }

        private static Stream<List<RDFNode>> lists(Model model, Resource type, Property predicate) {
            return statements(model, null, predicate, null)
                    .filter(s -> type == null || s.getSubject().hasProperty(RDF.type, type))
                    .map(Statement::getObject)
                    .filter(n -> n.canAs(RDFList.class))
                    .map(n -> n.as(RDFList.class).asJavaList());
        }

        private static Stream<Resource> multiSubjects(Model model, Resource object, Property... predicates) {
            return Stream.of(predicates).map(p -> subjects(model, p, object)).flatMap(Function.identity());
        }

        private static Stream<Resource> multiObjects(Model model, Resource subject, Property... predicates) {
            return Stream.of(predicates).map(p -> objects(model, subject, p)).flatMap(Function.identity());
        }

        public static Stream<Statement> statements(Model m, Resource s, Property p, RDFNode o) {
            return Streams.asStream(m.listStatements(s, p, o));
        }

        public static Stream<Resource> objects(Model model, Resource subject, Property predicate) {
            return statements(model, subject, predicate, null).map(Statement::getObject).filter(RDFNode::isResource).map(RDFNode::asResource);
        }

        public static Stream<Resource> subjects(Model model, Property predicate, Resource object) {
            return statements(model, null, predicate, object).map(Statement::getSubject).filter(RDFNode::isResource).map(RDFNode::asResource);
        }

        public static Stream<Resource> subjects(Model model, Property predicate) {
            return subjects(model, predicate, null);
        }

    }

}
