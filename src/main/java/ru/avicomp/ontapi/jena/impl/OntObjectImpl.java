package ru.avicomp.ontapi.jena.impl;

import java.util.Arrays;
import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.enhanced.EnhNode;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDF;

import ru.avicomp.ontapi.OntException;
import ru.avicomp.ontapi.jena.JenaUtils;
import ru.avicomp.ontapi.jena.impl.configuration.OntObjectFactory;
import ru.avicomp.ontapi.jena.model.OntIndividual;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntObject;

/**
 * base resource.
 * <p>
 * Created by szuev on 03.11.2016.
 */
public class OntObjectImpl extends ResourceImpl implements OntObject {
    static final Node RDF_TYPE = RDF.type.asNode();
    static final Node OWL_DATATYPE_PROPERTY = OWL2.DatatypeProperty.asNode();
    static final Node OWL_OBJECT_PROPERTY = OWL2.ObjectProperty.asNode();
    static final Node OWL_CLASS = OWL2.Class.asNode();
    static final Node OWL_RESTRICTION = OWL2.Restriction.asNode();

    public static OntObjectFactory objectFactory = new OntObjectFactory() {
        @Override
        public Stream<EnhNode> find(EnhGraph eg) {
            return JenaUtils.asStream(eg.asGraph().find(Node.ANY, Node.ANY, Node.ANY).
                    mapWith(Triple::getSubject).filterKeep(n -> canWrap(n, eg)).mapWith(n -> wrap(n, eg)));
        }

        @Override
        public EnhNode wrap(Node n, EnhGraph eg) {
            if (canWrap(n, eg)) {
                return new OntObjectImpl(n, eg);
            }
            throw new OntException("Cannot convert node " + n + " to OntObject");
        }

        @Override
        public boolean canWrap(Node node, EnhGraph eg) {
            return node.isURI() || node.isBlank();
        }
    };

    public OntObjectImpl(Node n, EnhGraph m) {
        super(n, m);
    }

    public Stream<Resource> types() {
        return JenaUtils.asStream(getModel().listObjectsOfProperty(this, RDF.type)
                .filterKeep(RDFNode::isURIResource).mapWith(Resource.class::cast));
    }

    boolean hasType(Resource type) {
        return types().filter(type::equals).findAny().isPresent();
    }

    void addType(Resource type) {
        getModel().add(this, RDF.type, type);
    }

    void removeType(Resource type) {
        getModel().remove(this, RDF.type, type);
    }

    void changeType(Resource property, boolean add) {
        if (add) {
            addType(property);
        } else {
            removeType(property);
        }
    }

    @Override
    public GraphModelImpl getModel() {
        return (GraphModelImpl) super.getModel();
    }

    @SuppressWarnings("unchecked")
    public Class<? extends OntObject> getActualClass() {
        return Arrays.stream(getClass().getInterfaces()).filter(OntObject.class::isAssignableFrom).map(c -> (Class<? extends OntObject>) c).findFirst().orElse(null);
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", asNode(), getActualClass().getSimpleName());
    }

    public <T extends OntObject> T getOntProperty(Property predicate, Class<T> view) {
        Statement st = getProperty(predicate);
        return st == null ? null : getModel().getNodeAs(st.getObject().asNode(), view);
    }

    public <T extends OntObject> T getRequiredOntProperty(Property predicate, Class<T> view) {
        return getModel().getNodeAs(getRequiredProperty(predicate).getObject().asNode(), view);
    }

    @Override
    public Stream<RDFNode> annotations(OntNAP property) {
        return JenaUtils.asStream(mustBeURI().listProperties(property).mapWith(Statement::getObject)).distinct();
    }

    @Override
    public void addAnnotation(OntNAP property, Resource uri) {
        mustBeURI().addProperty(property, checkNamed(uri));
    }

    @Override
    public void addAnnotation(OntNAP property, Literal literal) {
        mustBeURI().addProperty(property, literal);
    }

    @Override
    public void addAnnotation(OntNAP property, OntIndividual.Anonymous anon) {
        mustBeURI().addProperty(property, anon);
    }

    OntObject mustBeURI() {
        if (isURIResource()) return this;
        throw new OntException("Resource must be uri");
    }

    static Node checkNamed(Node res) {
        if (OntException.notNull(res, "Null node").isURI()) {
            return res;
        }
        throw new OntException("Not uri node " + res);
    }

    static Resource checkNamed(Resource res) {
        if (OntException.notNull(res, "Null resource").isURIResource()) {
            return res;
        }
        throw new OntException("Not uri resource " + res);
    }
}
