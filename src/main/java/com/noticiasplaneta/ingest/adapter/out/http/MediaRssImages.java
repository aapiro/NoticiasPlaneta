package com.noticiasplaneta.ingest.adapter.out.http;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Extrae la foto principal de cada entrada de un feed RSS/Atom.
 *
 * <p>Rome no expone el namespace Media RSS ({@code media:content}) ni en los
 * enclosures ni en el foreign markup, así que se lee aparte con StAX. Se indexa por
 * {@code guid} y por {@code link}, porque Rome da el guid en {@code getUri()} y el
 * enlace en {@code getLink()} y no todos los feeds traen los dos.
 *
 * <p>Solo se aceptan imágenes: un {@code media:content} de tipo vídeo se ignora.
 */
final class MediaRssImages {

    private static final String MEDIA_NS = "http://search.yahoo.com/mrss/";

    private MediaRssImages() {
    }

    /** @return clave (guid o link) → url de la imagen. Nunca {@code null}. */
    static Map<String, String> extract(byte[] feedXml) {
        Map<String, String> images = new HashMap<>();
        if (feedXml == null || feedXml.length == 0) return images;

        XMLInputFactory factory = XMLInputFactory.newFactory();
        // Un feed es entrada no confiable: sin DTD ni entidades externas.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        try (ByteArrayInputStream in = new ByteArrayInputStream(feedXml)) {
            XMLStreamReader reader = factory.createXMLStreamReader(in);
            String guid = null;
            String link = null;
            String image = null;
            boolean insideEntry = false;

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();

                    if ("item".equals(name) || "entry".equals(name)) {
                        insideEntry = true;
                        guid = null;
                        link = null;
                        image = null;
                    } else if (insideEntry && guid == null && "guid".equals(name)) {
                        guid = texto(reader);
                    } else if (insideEntry && link == null && "link".equals(name)) {
                        // RSS: <link>texto</link>. Atom: <link href="..."/>
                        String href = reader.getAttributeValue(null, "href");
                        link = href != null ? href : texto(reader);
                    } else if (insideEntry && image == null
                            && MEDIA_NS.equals(reader.getNamespaceURI())
                            && ("content".equals(name) || "thumbnail".equals(name))) {

                        String url = reader.getAttributeValue(null, "url");
                        String medium = reader.getAttributeValue(null, "medium");
                        String type = reader.getAttributeValue(null, "type");
                        boolean esImagen = (medium == null || "image".equalsIgnoreCase(medium))
                                && (type == null || type.toLowerCase().startsWith("image/"));
                        if (url != null && esImagen) {
                            image = url;
                        }
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("item".equals(name) || "entry".equals(name)) {
                        if (image != null) {
                            if (guid != null && !guid.isBlank()) images.put(guid, image);
                            if (link != null && !link.isBlank()) images.put(link, image);
                        }
                        insideEntry = false;
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            // Un feed con XML raro no debe tumbar la ingesta: sin fotos, pero sin error.
            return images;
        }

        return images;
    }

    private static String texto(XMLStreamReader reader) {
        try {
            return reader.getElementText().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
