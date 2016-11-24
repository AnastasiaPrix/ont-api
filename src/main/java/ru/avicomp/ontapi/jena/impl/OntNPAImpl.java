package ru.avicomp.ontapi.jena.impl;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;

import ru.avicomp.ontapi.jena.impl.configuration.*;
import ru.avicomp.ontapi.jena.model.*;

/**
 * Negative Property Assertion Implementation.
 * <p>
 * Created by @szuev on 15.11.2016.
 */
public abstract class OntNPAImpl<P extends OntPE, T extends RDFNode> extends OntObjectImpl implements OntNPA<P, T> {
    private static OntFinder NPA_FINDER = new OntFinder.ByType(OWL2.NegativePropertyAssertion);
    private static OntFilter NPA_FILTER = OntFilter.BLANK
            .and(new OntFilter.HasPredicate(OWL2.sourceIndividual))
            .and(new OntFilter.HasPredicate(OWL2.assertionProperty));

    public static OntObjectFactory objectNPAFactory = new CommonOntObjectFactory(new OntMaker.Default(ObjectAssertionImpl.class), NPA_FINDER, NPA_FILTER, new OntFilter.HasPredicate(OWL2.targetIndividual));
    public static OntObjectFactory dataNPAFactory = new CommonOntObjectFactory(new OntMaker.Default(DataAssertionImpl.class), NPA_FINDER, NPA_FILTER, new OntFilter.HasPredicate(OWL2.targetValue));
    public static OntObjectFactory abstractNPAFactory = new MultiOntObjectFactory(NPA_FINDER, objectNPAFactory, dataNPAFactory);

    public OntNPAImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    public static DataAssertion create(OntGraphModelImpl model, OntIndividual source, OntNDP property, Literal target) {
        Resource res = create(model, source);
        res.addProperty(OWL2.assertionProperty, property);
        res.addProperty(OWL2.targetValue, target);
        return model.getNodeAs(res.asNode(), DataAssertion.class);
    }

    public static ObjectAssertion create(OntGraphModelImpl model, OntIndividual source, OntOPE property, OntIndividual target) {
        Resource res = create(model, source);
        res.addProperty(OWL2.assertionProperty, property);
        res.addProperty(OWL2.targetIndividual, target);
        return model.getNodeAs(res.asNode(), ObjectAssertion.class);
    }

    abstract Class<P> propertyClass();

    @Override
    public OntIndividual getSource() {
        return getRequiredOntProperty(OWL2.sourceIndividual, OntIndividual.class);
    }

    @Override
    public P getProperty() {
        return getRequiredOntProperty(OWL2.assertionProperty, propertyClass());
    }

    private static Resource create(OntGraphModel model, OntIndividual source) {
        Resource res = model.createResource();
        res.addProperty(RDF.type, OWL2.NegativePropertyAssertion);
        res.addProperty(OWL2.sourceIndividual, source);
        return res;
    }

    public static class ObjectAssertionImpl extends OntNPAImpl<OntOPE, OntIndividual> implements ObjectAssertion {
        public ObjectAssertionImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        Class<OntOPE> propertyClass() {
            return OntOPE.class;
        }

        @Override
        public Class<ObjectAssertion> getActualClass() {
            return ObjectAssertion.class;
        }


        @Override
        public OntIndividual getTarget() {
            return getRequiredOntProperty(OWL2.targetIndividual, OntIndividual.class);
        }

    }

    public static class DataAssertionImpl extends OntNPAImpl<OntNDP, Literal> implements DataAssertion {
        public DataAssertionImpl(Node n, EnhGraph m) {
            super(n, m);
        }

        @Override
        Class<OntNDP> propertyClass() {
            return OntNDP.class;
        }

        @Override
        public Class<DataAssertion> getActualClass() {
            return DataAssertion.class;
        }


        @Override
        public Literal getTarget() {
            return getRequiredOntProperty(OWL2.targetValue, Literal.class);
        }

    }
}
