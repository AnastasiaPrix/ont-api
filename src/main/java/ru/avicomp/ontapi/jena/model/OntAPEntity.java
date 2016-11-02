package ru.avicomp.ontapi.jena.model;

import java.util.stream.Stream;

import org.apache.jena.rdf.model.Resource;

/**
 * Annotation Property resource.
 * <p>
 * Created by szuev on 01.11.2016.
 */
public interface OntAPEntity extends OntPropertyEntity {
    @Override
    Stream<Resource> domain();

    @Override
    Stream<Resource> range();

    @Override
    default Type getOntType() {
        return Type.ANNOTATION_PROPERTY;
    }
}
