package com.tailf.packages.ned.netsimgen;

import com.tailf.conf.ConfNamespace;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiSchemas;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import org.xml.sax.InputSource;

final class Xml2Maapi {

    static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // secure defaults
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        return b.parse(new InputSource(new StringReader(xml)));
    }

    /** Return the concatenated XML of all children under the RESTCONF <data> element. */
    static String unwrapRestconfData(String xml) throws Exception {
        if (xml == null) return "";
        String trimmed = xml.trim();
        if (trimmed.isEmpty()) return "";

        Document doc = parseXml(trimmed);
        Element root = doc.getDocumentElement();
        if (root == null) return "";

        // If server returned <data> (RFC 8040 top-level), return its element children.
        boolean rootIsData = "data".equals(root.getLocalName());
        if (rootIsData) {
            return serializeChildren(root);
        } else {
            // Many servers return the resource element directly (e.g., <interfaces>...</interfaces>)
            // Return the root element as-is so callers can match on it.
            return serializeElement(root);
        }
    }

    private static String serializeChildren(Element parent) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        Node child = parent.getFirstChild();
        boolean wrote = false;
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                t.transform(new DOMSource(child), new StreamResult(sw));
                wrote = true;
            }
            child = child.getNextSibling();
        }
        return wrote ? sw.toString() : "";
    }

    /** Serialize one element to string. */
    static String serializeElement(Element e) throws Exception {
        Transformer t = TransformerFactory.newInstance().newTransformer();
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(e), new StreamResult(sw));
        return sw.toString();
    }

    /**
     * Walk a RESTCONF yang-data XML subtree and populate MAAPI.
     * We use the schema to handle lists (derive key leaf names and values).
     */
    static void applyModuleSubtree(Maapi mm, int th,
                                   ConfNamespace baseNs,
                                   MaapiSchemas.CSNode baseCs,
                                   String basePathString,
                                   Element elem) throws Exception {
        // Start at /…/config/<modulePrefix:tag>
        String modulePrefix = baseNs.prefix();
        String topTag = baseCs.getTag();  // local name in schema
        if (!topTag.equals(elem.getLocalName())) {
            // skip unexpected element
            return;
        }
        String topPath = basePathString + "/" + modulePrefix + ":" + topTag;
        ensureCreate(mm, th, topPath);
        walkChildren(mm, th, topPath, baseCs, elem);
    }

    private static void walkChildren(Maapi mm, int th,
                                    String parentPath,
                                    MaapiSchemas.CSNode parentCs,
                                    Element parentElem) throws Exception {
        // If we are currently inside a list entry, collect its key leaf names.
        java.util.Set<String> parentListKeys = java.util.Collections.emptySet();
        if (parentCs.isList()) {
            java.util.LinkedHashSet<String> ks = new java.util.LinkedHashSet<>();
            for (int i = 0; ; i++) {
                try {
                    MaapiSchemas.CSNode k = parentCs.getKey(i);
                    if (k == null) break;  // some impls return null at end
                    ks.add(k.getTag());
                } catch (Exception ignore) {
                    break;  // others throw at end
                }
            }
            parentListKeys = ks;
        }

        // Group children by local-name
        java.util.Map<String, java.util.List<Element>> groups = new java.util.LinkedHashMap<>();
        Node n = parentElem.getFirstChild();
        while (n != null) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element)n;
                groups.computeIfAbsent(e.getLocalName(), k -> new java.util.ArrayList<>()).add(e);
            }
            n = n.getNextSibling();
        }

        for (var entry : groups.entrySet()) {
            String local = entry.getKey();
            java.util.List<Element> elems = entry.getValue();

            MaapiSchemas.CSNode childCs = findChildByLocal(parentCs, local);
            if (childCs == null) continue;

            if (childCs.isLeaf()) {
                // **Skip key leaves** — keys are already carried in the instance path.
                if (parentCs.isList() && parentListKeys.contains(childCs.getTag())) {
                    continue;
                }
                String val = text(elems.get(0));
                if (val != null) {
                    String leafPath = parentPath + "/" + childCs.getTag();
                    mm.setElem(th, val, leafPath);
                }

            } else if (childCs.isLeafList()) {
                // (Keys are never leaf-lists in YANG, but be defensive anyway)
                if (parentCs.isList() && parentListKeys.contains(childCs.getTag())) {
                    continue;
                }
                java.util.List<String> values = new java.util.ArrayList<>();
                for (Element e : elems) {
                    String v = text(e);
                    if (v != null) values.add(v);
                }
                if (!values.isEmpty()) {
                    String leafListPath = parentPath + "/" + childCs.getTag();
                    mm.setElem(th, String.join(" ", values), leafListPath);
                }

            } else if (childCs.isList()) {
                // Build list instance path using schema key order
                java.util.List<String> keyNames = new java.util.ArrayList<>();
                for (int i = 0; ; i++) {
                    try {
                        MaapiSchemas.CSNode keyNode = childCs.getKey(i);
                        if (keyNode == null) break;
                        keyNames.add(keyNode.getTag());
                    } catch (Exception ignore) {
                        break;
                    }
                }

                for (Element e : elems) {
                    String keyStr = keysFromChild(e, keyNames);
                    if (keyStr == null) continue; // malformed entry; skip
                    String listPath = parentPath + "/" + childCs.getTag() + "{" + keyStr + "}";
                    ensureCreate(mm, th, listPath);
                    // Recurse into this list entry; inside, key leaves will be skipped by logic above
                    walkChildren(mm, th, listPath, childCs, e);
                }

            } else if (childCs.isContainer()) {
                String contPath = parentPath + "/" + childCs.getTag();
                ensureCreate(mm, th, contPath);
                for (Element e : elems) {
                    walkChildren(mm, th, contPath, childCs, e);
                }
            }
        }
    }

    private static String text(Element e) {
        String s = e.getTextContent();
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String keysFromChild(Element e, java.util.List<String> keyNames) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (String k : keyNames) {
            NodeList nl = e.getElementsByTagNameNS(e.getNamespaceURI(), k);
            if (nl.getLength() == 0) return null;   // missing key
            String v = nl.item(0).getTextContent();
            if (v == null) return null;
            parts.add(v.trim());
        }
        return String.join(" ", parts);  // NSO list instance key format
    }

    private static void ensureCreate(Maapi mm, int th, String path) throws Exception {
        try { mm.safeCreate(th, path); } catch (Exception ignore) {}
    }

    private static MaapiSchemas.CSNode findChildByLocal(MaapiSchemas.CSNode parent, String local) {
        for (MaapiSchemas.CSNode c : parent.getChildren()) {
            if (local.equals(c.getTag())) return c;
        }
        return null;
    }
}
