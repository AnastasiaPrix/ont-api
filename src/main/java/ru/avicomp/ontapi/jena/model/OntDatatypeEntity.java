package ru.avicomp.ontapi.jena.model;

/**
 * Datatype Resource.
 * <p>
 * Created by szuev on 01.11.2016.
 */
public interface OntDatatypeEntity extends OntEntity, OntDR {
    @Override
    default OntEntity.Type getOntType() {
        return OntEntity.Type.DATATYPE;
    }
}
