package ru.avicomp.ontapi.utils;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.log4j.Logger;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import ru.avicomp.ontapi.OntException;
import ru.avicomp.ontapi.io.OntFormat;

/**
 * Created by @szuev on 27.09.2016.
 */
public class ReadWriteUtils {
    private static final Logger LOGGER = Logger.getLogger(ReadWriteUtils.class);

    private static final String DESTINATION_DIR = "out";

    public static void print(OWLOntology ontology) {
        print(ontology, null);
    }

    public static void print(Model model) {
        print(model, null);
    }

    public static void print(OWLOntology ontology, OntFormat ext) {
        LOGGER.debug("\n" + toString(ontology, ext));
    }

    public static void print(Model model, OntFormat ext) {
        LOGGER.debug("\n" + toString(model, ext));
    }

    private static String toString(OWLOntology ontology, OntFormat type) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            OWLDocumentFormat format = type == null ? new TurtleDocumentFormat() : type.getOwlFormat();
            ontology.getOWLOntologyManager().saveOntology(ontology, format, out);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (OWLOntologyStorageException | IOException e) {
            throw new OntException(e);
        }
    }

    private static String toString(Model model, OntFormat ext) {
        return toStringWriter(model, ext).toString();
    }

    public static StringWriter toStringWriter(Model model, OntFormat ext) {
        StringWriter sw = new StringWriter();
        model.write(sw, (ext == null ? OntFormat.TTL_RDF : ext).getType(), null);
        return sw;
    }

    public static InputStream toInputStream(Model model, OntFormat ext) {
        try {
            return IOUtils.toInputStream(toString(model, ext), StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            throw new OntException(e);
        }
    }

    private static File getFileToSave(String name, OntFormat type) {
        File dir = new File(DESTINATION_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }
        return new File(dir, name + (type != null ? "." + type.getExt() : ""));
    }

    public static File getResourceFile(String projectDirName, String fileName) throws URISyntaxException, FileNotFoundException {
        URL url = ReadWriteUtils.class.getResource(projectDirName.startsWith("/") ? projectDirName : "/" + projectDirName);
        if (url == null)
            throw new IllegalArgumentException("Can't find project " + projectDirName + ".");
        File dir = new File(url.toURI());
        LOGGER.debug("Directory: " + dir);
        File res = new File(dir, fileName);
        if (!res.exists()) throw new FileNotFoundException(fileName);
        return res;
    }

    public static File getResourceFile(String fileName) {
        try {
            return getResourceFile("", fileName);
        } catch (URISyntaxException | FileNotFoundException e) {
            LOGGER.fatal(e);
        }
        return null;
    }

    public static URI getResourceURI(String dir, String file) {
        try {
            return getResourceFile(dir, file).toURI();
        } catch (URISyntaxException | FileNotFoundException e) {
            LOGGER.fatal(e);
        }
        return null;
    }

    public static URI getResourceURI(String file) {
        return getResourceURI("", file);
    }

    public static URI getOutURI(String file) {
        return new File(DESTINATION_DIR, file).toURI();
    }

    public static OntModel load(OntModelSpec spec, File file, OntFormat format) {
        Model base = ModelFactory.createDefaultModel();
        try {
            LOGGER.debug("Load model from " + file.toURI());
            base.read(new FileInputStream(file), null, format.getType());
        } catch (FileNotFoundException e) {
            LOGGER.fatal("Can't read ontology", e);
            throw new IllegalArgumentException(e);
        }
        return ModelFactory.createOntologyModel(spec, base);
    }

    public static void save(Model model, String name, OntFormat type) {
        File dst = getFileToSave(name, type);
        LOGGER.debug("Save model to " + dst.toURI() + " (" + type.getType() + ")");
        try (FileWriter out = new FileWriter(dst)) {
            model.write(out, type.getType());
        } catch (IOException e) {
            LOGGER.fatal("Unable to save model " + name, e);
        }
    }

    public static void save(OWLOntology ontology, String name, OntFormat type) {
        File dst = getFileToSave(name, type);
        LOGGER.debug("Save owl-ontology to " + dst.toURI() + " (" + (type == null ? "TURTLE" : type.getType()) + ")");
        OWLDocumentFormat format = type == null ? new TurtleDocumentFormat() : type.getOwlFormat();
        try (FileOutputStream out = new FileOutputStream(dst)) {
            ontology.getOWLOntologyManager().saveOntology(ontology, format, out);
        } catch (OWLOntologyStorageException | IOException e) {
            LOGGER.fatal("Unable to print owl-ontology " + ontology, e);
        }
    }

}
