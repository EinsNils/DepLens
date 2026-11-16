package io.github.einsnils.deplens.security;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/** Liest die Versionsliste aus einer {@code maven-metadata.xml} von Maven Central. */
public final class MavenMetadataParser {
    private MavenMetadataParser() {
    }

    public static List<MavenVersion> parse(String xml) throws ResponseFormatException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList nodes = document.getElementsByTagName("version");
            List<MavenVersion> versions = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String text = node.getTextContent().strip();
                if ("versions".equals(node.getParentNode().getNodeName()) && !text.isEmpty()) {
                    versions.add(MavenVersion.of(text));
                }
            }
            return versions;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new ResponseFormatException("Ungültige maven-metadata.xml", e);
        }
    }
}
