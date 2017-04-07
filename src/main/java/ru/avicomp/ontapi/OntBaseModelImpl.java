package ru.avicomp.ontapi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.jena.graph.Graph;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.AxiomAnnotations;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.model.parameters.Navigation;
import org.semanticweb.owlapi.model.parameters.OntologyCopy;
import org.semanticweb.owlapi.search.Filters;
import org.semanticweb.owlapi.util.OWLAxiomSearchFilter;

import ru.avicomp.ontapi.internal.ConfigProvider;
import ru.avicomp.ontapi.internal.InternalModel;
import ru.avicomp.ontapi.internal.InternalModelHolder;
import ru.avicomp.ontapi.jena.OntFactory;
import ru.avicomp.ontapi.jena.model.OntID;
import uk.ac.manchester.cs.owl.owlapi.OWLObjectImpl;

/**
 * 'Immutable' ontology with methods to read information in the form of OWL-Objects from graph-model.
 * It's our analogy of {@link uk.ac.manchester.cs.owl.owlapi.OWLImmutableOntologyImpl}
 * <p>
 * Created by @szuev on 03.12.2016.
 */
@SuppressWarnings("WeakerAccess")
public class OntBaseModelImpl extends OWLObjectImpl implements OWLOntology, ConfigProvider, InternalModelHolder {
    // binary format to provide serialization:
    protected static final OntFormat DEFAULT_SERIALIZATION_FORMAT = OntFormat.RDF_THRIFT;

    protected transient InternalModel base;
    protected transient OntologyManagerImpl managerBackCopy;

    protected OWLOntologyID ontologyID;

    public OntBaseModelImpl(OntologyManagerImpl manager, OWLOntologyID ontologyID) {
        OntApiException.notNull(ontologyID, "Null OWL ID.");
        this.base = new InternalModel(OntFactory.createDefaultGraph(), OntApiException.notNull(manager, "Null manager.").createModelConfig());
        setOntologyID(ontologyID);
    }

    public OntBaseModelImpl(Graph graph, OntologyManagerImpl.ModelConfig conf) {
        this.base = new InternalModel(OntApiException.notNull(graph, "Null graph."), OntApiException.notNull(conf, "Null conf."));
    }

    @Override
    public InternalModel getBase() {
        return base;
    }

    @Override
    public void setBase(InternalModel m) {
        base = m;
    }

    @Override
    public OntologyManagerImpl.ModelConfig getConfig() {
        return (OntologyManagerImpl.ModelConfig) base.getConfig();
    }

    @Override
    public OntologyManager getOWLOntologyManager() {
        return getConfig().manager();
    }

    /**
     * Sets the manager.
     * The parameter could be null (e.g. during {@link OWLOntologyManager#clearOntologies})
     * Used also during {@link OWLOntologyManager#copyOntology(OWLOntology, OntologyCopy)}
     *
     * @param manager {@link OntologyManager}, nullable.
     * @see uk.ac.manchester.cs.owl.owlapi.OWLOntologyManagerImpl#copyOntology(OWLOntology, OntologyCopy)
     * @throws OntApiException in case wrong manager specified.
     */
    @Override
    public void setOWLOntologyManager(OWLOntologyManager manager) {
        if (Objects.equals(getOWLOntologyManager(), manager)) return;
        OntologyManagerImpl m;
        try {
            m = (OntologyManagerImpl) manager;
        } catch (ClassCastException ce) {
            if (this.managerBackCopy != null) {
                // rollback changes made while coping (inside OWL-API 5.0.5)
                this.managerBackCopy.rollBackMoving(this, manager);
                getConfig().setManager(this.managerBackCopy);
                this.managerBackCopy = null;
            }
            throw new OntApiException("Trying to move? Don't do it!", ce);
        }
        this.managerBackCopy = getConfig().setManager(m);
    }

    /**
     * Gets ID.
     * Does not just return cached {@link #ontologyID} to provide synchronization with encapsulated jena model ({@link #base}).
     * In the other hand we need this cached {@link #ontologyID} to be existed and relevant for owl serialization.
     *
     * @return the {@link OWLOntologyID}
     */
    @Override
    public OWLOntologyID getOntologyID() {
        OntID id = base.getID();
        if (id.isAnon()) {
            return ontologyID == null || !ontologyID.isAnonymous() ? ontologyID = new OWLOntologyID() : ontologyID;
        }
        Optional<IRI> iri = Optional.of(id.getURI()).map(IRI::create);
        Optional<IRI> version = Optional.ofNullable(id.getVersionIRI()).map(IRI::create);
        return ontologyID = new OWLOntologyID(iri, version);
    }

    /**
     * Sets ID.
     * Protected access since this is an "immutable" ontology.
     *
     * @param id {@link OWLOntologyID}
     */
    protected void setOntologyID(OWLOntologyID id) {
        try {
            if (id.isAnonymous()) {
                base.setID(null).setVersionIRI(null);
                return;
            }
            IRI iri = id.getOntologyIRI().orElse(null);
            IRI versionIRI = id.getVersionIRI().orElse(null);
            base.setID(iri == null ? null : iri.getIRIString()).setVersionIRI(versionIRI == null ? null : versionIRI.getIRIString());
        } finally {
            ontologyID = id;
        }
    }

    @Override
    public boolean isAnonymous() {
        return base.getID().isAnon();
    }

    @Override
    public boolean isEmpty() {
        return base.isOntologyEmpty();
    }

    @Override
    public Stream<OWLAnnotation> annotations() {
        return base.annotations();
    }

    /*
     * =============================
     * Methods to work with imports:
     * =============================
     */

    @Override
    public Stream<OWLOntology> imports() {
        return getOWLOntologyManager().imports(this);
    }

    @Override
    public Stream<OWLImportsDeclaration> importsDeclarations() {
        return base.importDeclarations();
    }

    @Override
    public Stream<IRI> directImportsDocuments() {
        return importsDeclarations().map(OWLImportsDeclaration::getIRI);
    }

    @Override
    public Stream<OWLOntology> directImports() {
        return getOWLOntologyManager().directImports(this);
    }

    @Override
    public Stream<OWLOntology> importsClosure() {
        return getOWLOntologyManager().importsClosure(this);
    }

    /*
     * ==========================
     * To work with OWL-entities:
     * ==========================
     */

    @Override
    public Stream<OWLClass> classesInSignature() {
        return base.classes();
    }

    @Override
    public Stream<OWLAnonymousIndividual> anonymousIndividuals() {
        return base.anonymousIndividuals();
    }

    @Override
    public Stream<OWLAnonymousIndividual> referencedAnonymousIndividuals() {
        return anonymousIndividuals();
    }

    @Override
    public Stream<OWLNamedIndividual> individualsInSignature() {
        return base.namedIndividuals();
    }

    @Override
    public Stream<OWLDataProperty> dataPropertiesInSignature() {
        return base.dataProperties();
    }

    @Override
    public Stream<OWLObjectProperty> objectPropertiesInSignature() {
        return base.objectProperties();
    }

    @Override
    public Stream<OWLAnnotationProperty> annotationPropertiesInSignature() {
        return base.annotationProperties();
    }

    @Override
    public Stream<OWLDatatype> datatypesInSignature() {
        return base.datatypes();
    }

    @Override
    public Stream<OWLEntity> signature() {
        return Stream.of(classesInSignature(), objectPropertiesInSignature(), dataPropertiesInSignature(),
                individualsInSignature(), datatypesInSignature(), annotationPropertiesInSignature()).flatMap(Function.identity());
    }

    @Override
    public Stream<OWLEntity> entitiesInSignature(@Nullable IRI entityIRI) {
        return base.getEntities(entityIRI).stream();
    }

    @Override
    public Set<IRI> getPunnedIRIs(@Nonnull Imports imports) {
        return base.ambiguousEntities(Imports.INCLUDED.equals(imports)).map(Resource::getURI).map(IRI::create).collect(Collectors.toSet());
    }

    @Override
    public boolean isDeclared(@Nullable OWLEntity owlEntity) {
        return base.axioms(OWLDeclarationAxiom.class).map(OWLDeclarationAxiom::getEntity)
                .anyMatch(obj -> obj.equals(owlEntity));
    }

    @Override
    public boolean containsReference(@Nonnull OWLEntity entity) {
        return signature().anyMatch(entity::equals);
    }

    @Override
    public boolean containsClassInSignature(@Nonnull IRI iri) {
        return classesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsClassInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsClassInSignature(iri));
    }

    @Override
    public boolean containsObjectPropertyInSignature(@Nonnull IRI iri) {
        return objectPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsObjectPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsObjectPropertyInSignature(iri));
    }

    @Override
    public boolean containsDataPropertyInSignature(@Nonnull IRI iri) {
        return dataPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsDataPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsDataPropertyInSignature(iri));
    }

    @Override
    public boolean containsAnnotationPropertyInSignature(@Nonnull IRI iri) {
        return annotationPropertiesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsAnnotationPropertyInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsAnnotationPropertyInSignature(iri));
    }

    @Override
    public boolean containsDatatypeInSignature(@Nonnull IRI iri) {
        return datatypesInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsDatatypeInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsDatatypeInSignature(iri));
    }

    @Override
    public boolean containsIndividualInSignature(@Nonnull IRI iri) {
        return individualsInSignature().map(HasIRI::getIRI).anyMatch(iri::equals);
    }

    @Override
    public boolean containsIndividualInSignature(@Nonnull IRI iri, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.containsIndividualInSignature(iri));
    }

    /*
     * =======================
     * To work with OWL-Axioms
     * =======================
     */

    @Override
    public Stream<OWLAxiom> axioms() {
        return base.axioms();
    }

    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull AxiomType<T> axiomType) {
        return base.axioms(axiomType);
    }

    @Override
    public Stream<OWLClassAxiom> axioms(@Nonnull OWLClass clazz) {
        return base.classAxioms().filter(a -> OwlObjects.objects(OWLClass.class, a).anyMatch(clazz::equals));
    }

    @Override
    public Stream<OWLObjectPropertyAxiom> axioms(@Nonnull OWLObjectPropertyExpression property) {
        return base.objectPropertyAxioms().filter(a -> OwlObjects.objects(OWLObjectPropertyExpression.class, a).anyMatch(property::equals));
    }

    @Override
    public Stream<OWLDataPropertyAxiom> axioms(@Nonnull OWLDataProperty property) {
        return base.dataPropertyAxioms().filter(a -> OwlObjects.objects(OWLDataProperty.class, a).anyMatch(property::equals));
    }

    @Override
    public Stream<OWLIndividualAxiom> axioms(@Nonnull OWLIndividual individual) {
        return base.individualAxioms().filter(a -> OwlObjects.objects(OWLIndividual.class, a).anyMatch(individual::equals));
    }

    @Override
    public Stream<OWLDatatypeDefinitionAxiom> axioms(@Nonnull OWLDatatype datatype) {
        return base.axioms(OWLDatatypeDefinitionAxiom.class).filter(a -> datatype.equals(a.getDatatype()));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key) {
        return (Stream<T>) base.axioms(StreamSupport.stream(filter.getAxiomTypes().spliterator(), false)
                .map(type -> (AxiomType<T>) type)
                .collect(Collectors.toSet())).filter(a -> filter.pass(a, key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends OWLAxiom> Stream<T> axioms(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key, @Nonnull Imports imports) {
        return imports.stream(this).flatMap(o -> (Stream<T>) o.axioms(filter, key));
    }

    /**
     * Generic search method: results all axioms which refer object, are instances of type.
     * WARNING: it differs from original OWL-API method (see {@link uk.ac.manchester.cs.owl.owlapi.OWLImmutableOntologyImpl#axioms(Class, Class, OWLObject, Navigation)}).
     * For internal use only.
     *
     * @param type     {@link Class<OWLAxiom>}, not null, type of axioms.
     * @param view     {@link Class<OWLObject>}. anything. ignored.
     * @param object   {@link OWLObject} to find occurrences.
     * @param position {@link Navigation} used in conjunction with {@code object} for some several kinds of axioms.
     * @return Stream of {@link OWLAxiom}s
     * @see uk.ac.manchester.cs.owl.owlapi.OWLImmutableOntologyImpl#axioms(Class, Class, OWLObject, Navigation)
     * @see uk.ac.manchester.cs.owl.owlapi.Internals#get(Class, Class, Navigation)
     */
    @SuppressWarnings("unchecked")
    @Override
    public <A extends OWLAxiom> Stream<A> axioms(@Nonnull Class<A> type, @Nullable Class<? extends OWLObject> view, @Nonnull OWLObject object, @Nullable Navigation position) {
        if (OWLSubObjectPropertyOfAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubObjectPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubDataPropertyOfAxiom.class.equals(type) && OWLDataPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubDataPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubAnnotationPropertyOfAxiom.class.equals(type) && OWLAnnotationProperty.class.isInstance(object)) { // the difference: this axiom type is ignored in original OWL-API method:
            return (Stream<A>) base.axioms(OWLSubAnnotationPropertyOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperProperty() : a.getSubProperty()));
        }
        if (OWLSubClassOfAxiom.class.equals(type) && OWLClassExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubClassOfAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSuperClass() : a.getSubClass()));
        }
        if (OWLInverseObjectPropertiesAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLInverseObjectPropertiesAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getSecondProperty() : a.getFirstProperty()));
        }
        if (OWLObjectPropertyAssertionAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLObjectPropertyAssertionAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getObject() : a.getSubject()));
        }
        if (OWLNegativeObjectPropertyAssertionAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLNegativeObjectPropertyAssertionAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getObject() : a.getSubject()));
        }
        if (OWLAnnotationAssertionAxiom.class.equals(type) && OWLAnnotationObject.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLAnnotationAssertionAxiom.class)
                    .filter(a -> object.equals(Navigation.IN_SUPER_POSITION.equals(position) ? a.getValue() : a.getSubject()));
        }
        if (OWLDisjointUnionAxiom.class.equals(type) && OWLClassExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLDisjointUnionAxiom.class)
                    .filter(a -> Navigation.IN_SUPER_POSITION.equals(position) ? a.classExpressions().anyMatch(object::equals) : object.equals(a.getOWLClass()));
        }
        if (OWLSubPropertyChainOfAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) base.axioms(OWLSubPropertyChainOfAxiom.class)
                    .filter(a -> Navigation.IN_SUPER_POSITION.equals(position) ? a.getPropertyChain().stream().anyMatch(object::equals) : object.equals(a.getSuperProperty()));
        }
        if (OWLClassAxiom.class.equals(type) && OWLClass.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLClass) object);
        }
        if (OWLObjectPropertyAxiom.class.equals(type) && OWLObjectPropertyExpression.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLObjectPropertyExpression) object);
        }
        if (OWLDataPropertyAxiom.class.equals(type) && OWLDataProperty.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLDataProperty) object);
        }
        if (OWLIndividualAxiom.class.equals(type) && OWLIndividual.class.isInstance(object)) {
            return (Stream<A>) axioms((OWLIndividual) object);
        }
        if (OWLNaryAxiom.class.isAssignableFrom(type)) {
            return base.axioms(type)
                    .filter(a -> ((OWLNaryAxiom) a).operands().anyMatch(o -> Objects.equals(o, object)));
        }
        // default:
        return base.axioms(type).filter(a -> OwlObjects.objects(object.getClass(), a).anyMatch(object::equals));
    }

    @Override
    public Stream<OWLAxiom> tboxAxioms(@Nonnull Imports imports) {
        return AxiomType.TBoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLAxiom> aboxAxioms(@Nonnull Imports imports) {
        return AxiomType.ABoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLAxiom> rboxAxioms(@Nonnull Imports imports) {
        return AxiomType.RBoxAxiomTypes.stream().flatMap(t -> axioms(t, imports));
    }

    @Override
    public Stream<OWLLogicalAxiom> logicalAxioms() {
        return base.logicalAxioms();
    }

    @Override
    public Stream<OWLClassAxiom> generalClassAxioms() {
        Stream<OWLSubClassOfAxiom> subClassOfAxioms = base.axioms(OWLSubClassOfAxiom.class)
                .filter(a -> a.getSubClass().isAnonymous());
        Stream<? extends OWLNaryClassAxiom> naryClassAxioms = Stream.of(OWLEquivalentClassesAxiom.class, OWLDisjointClassesAxiom.class)
                .map(base::axioms).flatMap(Function.identity())
                .filter(a -> a.classExpressions().allMatch(IsAnonymous::isAnonymous));
        return Stream.concat(subClassOfAxioms, naryClassAxioms);
    }

    @Override
    public Stream<OWLAxiom> axiomsIgnoreAnnotations(@Nonnull OWLAxiom axiom) {
        return axioms(axiom.getAxiomType()).map(OWLAxiom.class::cast).filter(ax -> ax.equalsIgnoreAnnotations(axiom));
    }

    @Override
    public Stream<OWLAxiom> axiomsIgnoreAnnotations(@Nonnull OWLAxiom axiom, @Nonnull Imports imports) {
        return imports.stream(this).flatMap(o -> o.axiomsIgnoreAnnotations(axiom));
    }

    @Override
    public Stream<OWLAxiom> referencingAxioms(@Nonnull OWLPrimitive primitive) {
        if (primitive instanceof IRI) {
            return axioms().filter(a -> OwlObjects.iris(a).anyMatch(primitive::equals));
        }
        if (primitive instanceof OWLDatatype) { // as well as iri the datatype could be hidden inside other objects (literals):
            return axioms().filter(a -> OwlObjects.objects(OWLDatatype.class, a).anyMatch(primitive::equals));
        }
        return axioms().filter(a -> OwlObjects.objects(OWLPrimitive.class, a).anyMatch(primitive::equals));
    }

    @Override
    public Stream<OWLSubAnnotationPropertyOfAxiom> subAnnotationPropertyOfAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.subAnnotationWithSub, property);
    }

    @Override
    public Stream<OWLAnnotationPropertyDomainAxiom> annotationPropertyDomainAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.apDomainFilter, property);
    }

    @Override
    public Stream<OWLAnnotationPropertyRangeAxiom> annotationPropertyRangeAxioms(@Nonnull OWLAnnotationProperty property) {
        return axioms(Filters.apRangeFilter, property);
    }

    @Override
    public Stream<OWLDatatypeDefinitionAxiom> datatypeDefinitions(@Nonnull OWLDatatype datatype) {
        return axioms(Filters.datatypeDefFilter, datatype);
    }

    @Override
    public int getAxiomCount() {
        return (int) axioms().count();
    }

    @Override
    public int getAxiomCount(@Nonnull Imports imports) {
        return imports.stream(this).mapToInt(OWLAxiomCollection::getAxiomCount).sum();
    }

    @Override
    public <T extends OWLAxiom> int getAxiomCount(@Nonnull AxiomType<T> axiomType) {
        return (int) axioms(axiomType).count();
    }

    @Override
    public <T extends OWLAxiom> int getAxiomCount(@Nonnull AxiomType<T> axiomType, @Nonnull Imports imports) {
        return imports.stream(this).mapToInt(o -> o.getAxiomCount(axiomType)).sum();
    }

    @Override
    public int getLogicalAxiomCount() {
        return (int) logicalAxioms().count();
    }

    @Override
    public int getLogicalAxiomCount(@Nonnull Imports imports) {
        return imports.stream(this).mapToInt(OWLAxiomCollection::getLogicalAxiomCount).sum();
    }

    @Override
    public boolean containsAxiom(@Nullable OWLAxiom axiom) {
        return base.axioms().anyMatch(a -> a.equals(axiom));
    }

    @Override
    public boolean containsAxiom(@Nonnull OWLAxiom axiom, @Nonnull Imports imports, @Nonnull AxiomAnnotations ignoreAnnotations) {
        return imports.stream(this).anyMatch(o -> ignoreAnnotations.contains(o, axiom));
    }

    @Override
    public boolean containsAxiomIgnoreAnnotations(@Nonnull OWLAxiom axiom) {
        return containsAxiom(axiom) || axioms(axiom.getAxiomType()).anyMatch(ax -> ax.equalsIgnoreAnnotations(axiom));
    }

    @Override
    public boolean contains(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key) {
        return base.axioms(StreamSupport.stream(filter.getAxiomTypes().spliterator(), false)
                .map(type -> type)
                .collect(Collectors.toSet())).anyMatch(a -> filter.pass(a, key));
    }

    @Override
    public boolean contains(@Nonnull OWLAxiomSearchFilter filter, @Nonnull Object key, @Nonnull Imports imports) {
        return imports.stream(this).anyMatch(o -> o.contains(filter, key));
    }

    /*
     * ======================
     * Serialization methods:
     * ======================
     */

    /**
     * Reads the object while serialization.
     * Note: only base graph!
     *
     * @param in {@link ObjectInputStream}
     * @see OntologyManagerImpl#readObject(ObjectInputStream)
     * @see OntologyModelImpl.Concurrent#readObject(ObjectInputStream)
     */
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        Graph base = OntFactory.createDefaultGraph();
        RDFDataMgr.read(base, in, DEFAULT_SERIALIZATION_FORMAT.getLang());
        // set temporary model with default personality, it will be reset inside manager while its #readObject
        setBase(new InternalModel(base, ConfigProvider.DEFAULT));
    }

    /**
     * Writes the object while serialization.
     * Note: only base graph!
     *
     * @param out {@link ObjectOutputStream}
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject(); // serialize only base graph (it will be wrapped as UnionGraph):
        RDFDataMgr.write(out, base.getBaseGraph(), DEFAULT_SERIALIZATION_FORMAT.getLang());
    }

    /**
     * Overridden {@link super#toString()} in order not to force the axioms loading.
     * I believe that for brief information there should be a separate method
     * and the original implementation of toString is not very good idea.
     *
     * @return String
     */
    @Override
    public String toString() {
        return String.format("Ontology(%s)", ontologyID);
    }
}
