package ru.avicomp.ontapi.jena.impl;

import java.util.stream.Stream;

import org.apache.jena.enhanced.EnhGraph;
import org.apache.jena.graph.Node;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL2;
import org.apache.jena.vocabulary.RDFS;

import ru.avicomp.ontapi.jena.JenaUtils;
import ru.avicomp.ontapi.jena.model.OntNAP;
import ru.avicomp.ontapi.jena.model.OntStatement;

/**
 * owl:AnnotationProperty
 * <p>
 * Created by szuev on 03.11.2016.
 */
public class OntAPropertyImpl extends OntEntityImpl implements OntNAP {

    public OntAPropertyImpl(Node n, EnhGraph g) {
        super(OntObjectImpl.checkNamed(n), g);
    }

    @Override
    public Class<OntNAP> getActualClass() {
        return OntNAP.class;
    }

    @Override
    public Resource getRDFType() {
        return OWL2.AnnotationProperty;
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
        return JenaUtils.asStream(getModel().listObjectsOfProperty(this, RDFS.domain).mapWith(RDFNode::asResource));
    }

    @Override
    public Stream<Resource> range() {
        return JenaUtils.asStream(getModel().listObjectsOfProperty(this, RDFS.range).mapWith(RDFNode::asResource));
    }

    @Override
    public boolean isBuiltIn() {
        return BUILT_IN_ANNOTATION_PROPERTIES.contains(this);
    }

    @Override
    public Property inModel(Model m) {
        return getModel() == m ? this : m.createProperty(getURI());
    }
}
